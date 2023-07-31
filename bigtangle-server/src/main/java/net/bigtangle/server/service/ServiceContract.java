package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.script.Script;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.store.FullBlockStore;

public class ServiceContract extends ServiceBase {

	private static final Logger log = LoggerFactory.getLogger(ServiceBase.class);

	public ServiceContract(ServerConfiguration serverConfiguration, NetworkParameters networkParameters) {
		super(serverConfiguration, networkParameters);

	}

	/*
	 * the ContractEvent received and do next action
	 */
	public ContractResult executeContract(Block block, FullBlockStore blockStore, String contractid)
			throws BlockStoreException {
		if (ContractResult.ordermatch.equals(contractid)) {
			return orderMatching(block, blockStore);
		} else {
			Token contract = blockStore.getTokenID(contractid).get(0);
			String classname = getValue("classname", contract.getTokenKeyValues());
			if ("net.bigtangle.server.service.LotteryContract".equals(classname)) {
				return lotteryContract(block, blockStore, contract);
			}
			// TODO run others
			return null;
		}
	}

	/*
	 * 
	 */
	public ContractResult lotteryContract(Block block, FullBlockStore blockStore, Token contract)
			throws BlockStoreException {

		List<ContractEventRecord> records = calContractEventRecord(contract.getTokenid(), blockStore);
		String winnerAmount = getValue("winnerAmount", contract.getTokenKeyValues());
		String amount = getValue("amount", contract.getTokenKeyValues());
		List<ContractEventRecord> userRecord = new ArrayList<>();
		if (winnerAmount != null && canTakeWinner(records, userRecord, new BigInteger(winnerAmount))) {
			return doTakeWinner(block, blockStore, userRecord, new BigInteger(amount));
		}

		return null;
	}

	public String getValue(String key, TokenKeyValues kvs) {
		for (KeyValue k : kvs.getKeyvalues()) {
			if (key.equals(k.getKey())) {
				return k.getValue();
			}
		}
		return null;

	}

	/*
	 * can be check on each node, the winner, the winnerBlock hash calculate the
	 * unique userAddress, userUtxos for check and generate the dynamic outputs
	 */
	private ContractResult doTakeWinner(Block winnerBlock, FullBlockStore blockStore, List<ContractEventRecord> opens,
			BigInteger amount) {

		// Deterministic randomization
		long randomness = winnerBlock.getPrevBlockHash().toBigInteger().longValue();
		// .xor(winnerBlock.getPrevBranchBlockHash().toBigInteger());
		Random se = new Random(randomness);
		List<String> userlist = baseList(opens, amount);
		int randomWin = se.nextInt(userlist.size());
		log.debug("randomn win = " + randomWin + " userlist size =" + userlist.size());
		ContractEventRecord winner = findList(opens, userlist.get(randomWin));
		log.debug("winner = " + winner.toString());
		Transaction tx = createOrderPayoutTransaction(winnerBlock, winner,
				new Coin(sum(opens), winner.getTargetTokenid()));
		Set<Sha256Hash> spentContractEventRecord = new HashSet<>();
		for (ContractEventRecord o : opens) {
			spentContractEventRecord.add(o.getBlockHash());
		}

		return new ContractResult(winnerBlock.getHash(), winner.getContractTokenid(), spentContractEventRecord,
				tx.getHash(), tx, null, winnerBlock.getTimeSeconds());
	}

	/*
	 * condition for execute the lottery 1) no other pending payment 2) can do the
	 * send failed block again 3) the sum is ok
	 */
	private boolean canTakeWinner(List<ContractEventRecord> player, List<ContractEventRecord> userlist,
			BigInteger winnerAmount) {

		BigInteger sum = BigInteger.ZERO;
		for (ContractEventRecord u : player) {
			sum = sum.add(u.getTargetValue());
			userlist.add(u);
			if (sum.compareTo(winnerAmount) >= 0) {
				return true;
			}
		}
		// log.debug(" sum= " + sum);
		return false;

	}

	private List<String> baseList(List<ContractEventRecord> userlist, BigInteger baseAmount) {
		List<String> addresses = new ArrayList<String>();

		for (ContractEventRecord eventRecord : userlist) {
			int multi = eventRecord.getTargetValue().divide(baseAmount).intValue();
			for (int i = 0; i < multi; i++) {
				addresses.add(eventRecord.getBeneficiaryAddress());
			}
		}

		return addresses;
	}

	private ContractEventRecord findList(List<ContractEventRecord> userlist, String address) {
		for (ContractEventRecord u : userlist) {
			if (address.equals(u.getBeneficiaryAddress())) {
				return u;
			}
		}
		return null;
	}

	public Transaction createOrderPayoutTransaction(Block block, ContractEventRecord winner, Coin outCoin) {
		Transaction tx = new Transaction(networkParameters);

		tx.addOutput(outCoin, new Address(networkParameters, winner.getBeneficiaryAddress()));

		// The coinbase input does not really need to be a valid signature
		TransactionInput input = new TransactionInput(networkParameters, tx, Script
				.createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
		tx.addInput(input);
		tx.setMemo(new MemoInfo("contract"));

		return tx;
	}

	public BigInteger sum(List<ContractEventRecord> opens) {
		BigInteger sum = BigInteger.ZERO;
		for (ContractEventRecord u : opens) {
			sum = sum.add(u.getTargetValue());
		}
		return sum;
	}

	// calculate the open ContractEventRecord and must be repeatable for the
	// selected

	protected List<ContractEventRecord> calContractEventRecord(String contractTokenid, FullBlockStore store)
			throws BlockStoreException {

		return store.getOpenContractEvent(contractTokenid).stream()
				.sorted((o1, o2) -> o1.getBlockHash().compareTo(o2.getBlockHash())).collect(Collectors.toList());

	}

	public void checkContractExecute(Block block, FullBlockStore blockStore) {

		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());

			// blockStore.updateContractEvent( );
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
