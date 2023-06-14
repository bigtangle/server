/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service.apps.lottery;

import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.ContractRecord;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.store.FullBlockStore;

/*
 * start check balance and check to X amount and collect all user in lottery
 * list of (each ticket, address) compute random selection of winner pay to
 * winner address
 * winnerAmount is the minimum defined winnerAmount and paid can be more than this 
 * Defined as contract with  tokenid, winnerAmount, payAmount and consensus check
 * No sign for consensus method to winner and no the contract address is protected only consensus method.
 * consensus method will run and verify on each node
 */

public class LotteryExecution {

	private static final Logger log = LoggerFactory.getLogger(LotteryExecution.class);

	@Autowired
	protected StoreService storeService;
	@Autowired
	private NetworkParameters networkParameters;
	@Autowired
	private BlockService blockService;

	private String tokenid;

	private String winner;
	private List<ContractRecord> userUtxos;
	private BigInteger winnerAmount;
	private boolean macthed;
	private String contractTokenid;
	List<String> userAddress;

	public void execute() throws Exception {
		FullBlockStore store = storeService.getStore();

		try {
			List<ContractRecord> player = calContractRecord(contractTokenid, store);
			userUtxos = new ArrayList<>();
			if (canTakeWinner(player, userUtxos, store)) {
				doTakeWinner(store);
			}
		} finally {
			store.close();
		}
	}

	/*
	 * can be check on each node, the winner, the winnerBlock hash
	 * calculate the unique userAddress,  userUtxos for check and generate the dynamic outputs 
	 */
	private void doTakeWinner(FullBlockStore store) throws Exception {
		Token t = store.getTokenID(tokenid).get(0);

		userAddress = baseList(userUtxos, t);
		Block winnerBlock = blockService.getBlockPrototype(store);
		// Deterministic randomization
		byte[] randomness = Utils.xor(winnerBlock.getPrevBlockHash().getBytes(), winnerBlock.getPrevBranchBlockHash().getBytes());
		SecureRandom se = new SecureRandom(randomness);

		winner = userAddress.get(se.nextInt(userAddress.size()));

		log.debug("winner " + winner + " sum =" + sum() + " \n user address size: " + userAddress.size());

		// no sign for contract transaction Block b = batchGiveMoneyToECKeyList(winner, sum(), "win
		// lottery", userUtxos);

		// log.debug("block " + (b == null ? "block is null" : b.toString()));

	}

	/*
	 * split the list for lottery pay
	 */
	private List<String> baseList(List<ContractRecord> player, Token t) {
		List<String> addresses = new ArrayList<String>();
		for (ContractRecord u : player) {
			addresses.add(u.getBeneficiaryAddress());
		}
		return addresses;
	}

	 

	 
	public BigInteger sum() {
		BigInteger sum = BigInteger.ZERO;
		for (ContractRecord u : userUtxos) {
			sum = sum.add(BigInteger.valueOf(u.getTargetValue()));
		}
		return sum;
	}

	/*
	 * condition for execute the lottery 1) no other pending payment 2) can do the
	 * send failed block again 3) the sum is ok
	 */
	private boolean canTakeWinner(List<UTXO> player, List<UTXO> userlist) {

		BigInteger sum = BigInteger.ZERO;
		for (UTXO u : player) {
			if (checkUTXO(u)) {
				sum = sum.add(u.getValue().getValue());
				userlist.add(u);
				if (sum.compareTo(winnerAmount) >= 0) {
					return macthed = true;
				}
			}
		}
		log.debug(" sum= " + sum);
		return macthed = false;

	}

	/*
	 * condition for execute the lottery 1) no other pending payment 2) can do the
	 * send failed block again 3) the sum is ok
	 */
	private boolean canTakeWinner(List<ContractRecord> player, List<ContractRecord> userlist, FullBlockStore store) {

		BigInteger sum = BigInteger.ZERO;
		for (ContractRecord u : player) {
			 
		}
		log.debug(" sum= " + sum);
		return macthed = false;

	}

	private boolean checkUTXO(UTXO u) {
		return u.getFromaddress() != null && !"".equals(u.getFromaddress())
				&& !u.getFromaddress().equals(u.getAddress());
	}

	// calculate the open ContractRecord  and must be repeatable for the selected
 
	protected List<ContractRecord> calContractRecord(String contractTokenid, FullBlockStore store) throws Exception {
		ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(tokenid));
		Address address = ecKey.toAddress(networkParameters);
		List<ContractRecord> listUTXO = new ArrayList<>();
	 

		return listUTXO;

	}

	public String getTokenid() {
		return tokenid;
	}

	public void setTokenid(String tokenid) {
		this.tokenid = tokenid;
	}

	public String getWinner() {
		return winner;
	}

	public void setWinner(String winner) {
		this.winner = winner;
	}

	public BigInteger getWinnerAmount() {
		return winnerAmount;
	}

	public void setWinnerAmount(BigInteger winnerAmount) {
		this.winnerAmount = winnerAmount;
	}

	public boolean isMacthed() {
		return macthed;
	}

	public void setMacthed(boolean macthed) {
		this.macthed = macthed;
	}

	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

	public List<String> getUserAddress() {
		return userAddress;
	}

	public void setUserAddress(List<String> userAddress) {
		this.userAddress = userAddress;
	}

}
