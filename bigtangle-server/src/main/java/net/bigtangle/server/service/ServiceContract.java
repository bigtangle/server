package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Block;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.store.FullBlockStore;

public class ServiceContract extends ServiceBase {

	private static final Logger log = LoggerFactory.getLogger(ServiceBase.class);

	public ServiceContract(ServerConfiguration serverConfiguration, NetworkParameters networkParameters) {
		super(serverConfiguration, networkParameters);

	}

	/*
	 * the ContractEvent received and do next action
	 */
	public void connectContractEvent(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			ContractEventInfo reqInfo = new ContractEventInfo().parse(block.getTransactions().get(0).getData());
			Token contract = blockStore.getTokenID(reqInfo.getContractTokenid()).get(0);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	/*
	 * 
	 */
	public void lotteryContract(Block block, FullBlockStore blockStore,
			ContractEventInfo reqInfo, Token contract) throws Exception {

		List<ContractEventRecord> opens = calContractEventRecord(reqInfo.getContractTokenid(), blockStore);
		
	 
	 
			doTakeWinner(block, blockStore, opens); ;
		
	}

	/*
	 * can be check on each node, the winner, the winnerBlock hash calculate the
	 * unique userAddress, userUtxos for check and generate the dynamic outputs
	 */
	private void doTakeWinner(Block winnerBlock, FullBlockStore blockStore, List<ContractEventRecord> opens)
			throws Exception {

		// Deterministic randomization
		byte[] randomness = Utils.xor(winnerBlock.getPrevBlockHash().getBytes(),
				winnerBlock.getPrevBranchBlockHash().getBytes());
		SecureRandom se = new SecureRandom(randomness);

		ContractEventRecord winner = opens.get(se.nextInt(opens.size()));

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
		  {

		List<ContractEventRecord> listUTXO = new ArrayList<>();

		return listUTXO;

	}
}
