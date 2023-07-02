package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

		Token contract = blockStore.getTokenID(contractid).get(0);
		String classname = getValue("classname", contract.getTokenKeyValues());
		if ("net.bigtangle.server.service.LotteryContract".equals(classname)) {
			return lotteryContract(block, blockStore, contract);
		} else {
			// TODO run others
			return null;
		}

	}

	/*
	 * 
	 */
	public ContractResult lotteryContract(Block block, FullBlockStore blockStore, Token contract)
			throws BlockStoreException {

		List<ContractEventRecord> opens = calContractEventRecord(contract.getTokenid(), blockStore);
		String winnerAmount = getValue("winnerAmount", contract.getTokenKeyValues());
		if (winnerAmount != null && new BigInteger(winnerAmount).compareTo(sum(opens)) <= 0) {
			return doTakeWinner(block, blockStore, opens);
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
	private ContractResult doTakeWinner(Block winnerBlock, FullBlockStore blockStore, List<ContractEventRecord> opens) {

		// Deterministic randomization
		long randomness = winnerBlock.getPrevBlockHash().toBigInteger().longValue();
			//	.xor(winnerBlock.getPrevBranchBlockHash().toBigInteger());
		Random se = new Random(randomness);
		int randomWin = se.nextInt(opens.size());
		log.debug("randomn win = " + randomWin + " open size =" + opens.size());
		ContractEventRecord winner = opens.get(randomWin);
		log.debug("winner = " + winner.toString());
		Transaction tx = createOrderPayoutTransaction(winnerBlock, winner,
				new Coin(sum(opens), winner.getTargetTokenid()));
		List<Sha256Hash> spentContractEventRecord = new ArrayList<>();
		for (ContractEventRecord o : opens) {
			spentContractEventRecord.add(o.getBlockHash());
		}
		
		return new ContractResult(winnerBlock.getHash(), winner.getContractTokenid(), spentContractEventRecord,
				tx.getHash(), tx, null, winnerBlock.getTimeSeconds());
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
