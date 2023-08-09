package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
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
	public ContractResult executeContract(Block block, FullBlockStore blockStore, String contractid,
			Sha256Hash prevHash) throws BlockStoreException {
		if (ContractResult.ordermatch.equals(contractid)) {
			return orderMatching(block, prevHash, blockStore);
		} else {
			Token contract = blockStore.getTokenID(contractid).get(0);
			String classname = getValue("classname", contract.getTokenKeyValues());
			if ("net.bigtangle.server.service.LotteryContract".equals(classname)) {
				return lotteryContract(block, blockStore, contract, prevHash);
			}
			// TODO run others
			return null;
		}
	}

	/*
	 * 
	 */
	public ContractResult lotteryContract(Block block, FullBlockStore blockStore, Token contract, Sha256Hash prevHash)
			throws BlockStoreException {

		Set<ContractEventRecord> records = calContractEventRecord(contract.getTokenid(), prevHash, blockStore);
		String winnerAmount = getValue("winnerAmount", contract.getTokenKeyValues());
		String amount = getValue("amount", contract.getTokenKeyValues());
		Set<ContractEventRecord> userRecord = new HashSet<>();
		if (winnerAmount != null && canTakeWinner(records, userRecord, new BigInteger(winnerAmount))) {
			return doTakeWinner(block, blockStore, userRecord, new BigInteger(amount), prevHash, records);
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
	private ContractResult doTakeWinner(Block winnerBlock, FullBlockStore blockStore,
			Set<ContractEventRecord> usedRecords, BigInteger amount, Sha256Hash prevHash,
			Set<ContractEventRecord> allRecords) {

		// Deterministic randomization
		long randomness = winnerBlock.getTimeSeconds();
		// .xor(winnerBlock.getPrevBranchBlockHash().toBigInteger());
		Random se = new Random(randomness);
		List<String> userlist = baseList(usedRecords, amount);
		int randomWin = se.nextInt(userlist.size());
		log.debug("randomn win = " + randomWin + " userlist size =" + userlist.size());
		ContractEventRecord winner = findList(usedRecords, userlist.get(randomWin));
		log.debug("winner = " + winner.toString());
		Transaction tx = createOrderPayoutTransaction(winnerBlock, winner,
				new Coin(sum(usedRecords), winner.getTargetTokenid()));
		return new ContractResult(null, winner.getContractTokenid(),
				getContractEventRecordHash(allRecords), tx.getHash(), tx, prevHash, new HashSet<>(),
				getRemainder(allRecords, usedRecords), winnerBlock.getTimeSeconds(), null,
				getRemainderContractEventRecord(allRecords, usedRecords));
	}

	public Set<Sha256Hash> getContractEventRecordHash(Set<ContractEventRecord> orders) {
		Set<Sha256Hash> hashs = new HashSet<>();
		for (ContractEventRecord o : orders) {
			hashs.add(o.getBlockHash());
		}
		return hashs;
	}

	public Set<Sha256Hash> getRemainder(Set<ContractEventRecord> all, Set<ContractEventRecord> used) {
		Set<Sha256Hash> hashs = new HashSet<>();
		for (ContractEventRecord o : all) {
			if (!used.contains(o)) { 
				hashs.add(o.getBlockHash());

			}
		}
		return hashs;
	}

	public Set<ContractEventRecord> getRemainderContractEventRecord(Set<ContractEventRecord> all,
			Set<ContractEventRecord> used) {
		Set<ContractEventRecord> re = new HashSet<>();
		for (ContractEventRecord o : all) {
			if (!used.contains(o)) {
				o.setConfirmed(true);
				re.add(o);
			}
		}
		return re;
	}

	/*
	 * condition for execute the lottery 1) no other pending payment 2) can do the
	 * send failed block again 3) the sum is ok
	 */
	private boolean canTakeWinner(Collection<ContractEventRecord> player, Collection<ContractEventRecord> userlist,
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

	private List<String> baseList(Set<ContractEventRecord> userlist, BigInteger baseAmount) {
		List<String> addresses = new ArrayList<String>();

		for (ContractEventRecord eventRecord : userlist) {
			int multi = eventRecord.getTargetValue().divide(baseAmount).intValue();
			for (int i = 0; i < multi; i++) {
				addresses.add(eventRecord.getBeneficiaryAddress());
			}
		}

		return addresses;
	}

	private ContractEventRecord findList(Set<ContractEventRecord> userlist, String address) {
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

	public BigInteger sum(Collection<ContractEventRecord> opens) {
		BigInteger sum = BigInteger.ZERO;
		for (ContractEventRecord u : opens) {
			sum = sum.add(u.getTargetValue());
		}
		return sum;
	}

	// calculate the all open ContractEventRecord = new + previous remainder

	protected Set<ContractEventRecord> calContractEventRecord(String contractTokenid, Sha256Hash prevHash,
			FullBlockStore store) throws BlockStoreException {

		return store.getOpenContractEvent(contractTokenid, prevHash).stream()
				.sorted((o1, o2) -> o1.getBlockHash().compareTo(o2.getBlockHash())).collect(Collectors.toSet());

	}

}
