/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.response.GetContractEventInfoResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class LotteryExecution {

	private static final Logger log = LoggerFactory.getLogger(LotteryExecution.class);
	private String tokenid;
	private String contextRoot = "http://localhost:8088/";
	private Wallet walletAdmin;
	private NetworkParameters params;
	private String winner;
	private List<ContractEventInfo> userUtxos;
	private BigInteger winnerAmount;
	private boolean macthed;
	private String contractTokenid;
	List<String> userAddress;

	/*
	 * start check balance and check to X amount and collect all user in lottery
	 * list of (each ticket, address) compute random selection of winner pay to
	 * winner address
	 */
	public void start() throws Exception {
		ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(contractTokenid));
		walletAdmin = Wallet.fromKeys(params, ecKey);
	//	walletAdmin.setServerURL(contextRoot);
		List<ContractEventInfo> player = getContractEventInfo(contractTokenid);
		// TODO 100 millions raw
		// Must be sorted new UtilSort().sortContractEventInfo(player);
		userUtxos = new ArrayList<ContractEventInfo>();
		if (canTakeWinner(player, userUtxos)) {
			doTakeWinner();
		}
	}

	private void doTakeWinner() throws Exception {
		Token t = walletAdmin.checkTokenId(tokenid);

		userAddress = baseList(userUtxos, t);

		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		byte[] buf = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		Block r1 = params.getDefaultSerializer().makeBlock(buf);
		// Deterministic randomization
		byte[] randomness = Utils.xor(r1.getPrevBlockHash().getBytes(), r1.getPrevBranchBlockHash().getBytes());
		SecureRandom se = new SecureRandom(randomness);

		winner = userAddress.get(se.nextInt(userAddress.size()));

		log.debug("winner " + winner + " sum =" + sum() + " \n user address size: " + userAddress.size());

		Block b = batchGiveMoneyToECKeyList(winner, sum(), "win lottery", userUtxos);

		log.debug("block " + (b == null ? "block is null" : b.toString()));

	}

	/*
	 * split the list for lottery pay
	 */
	private List<String> baseList(List<ContractEventInfo> player, Token t) {
		List<String> addresses = new ArrayList<String>();
		for (ContractEventInfo u : player) {
			addresses.addAll(baseList(u, t));
		}
		return addresses;
	}

	private List<String> baseList(ContractEventInfo u, Token t) {
		List<String> addresses = new ArrayList<String>();

		BigInteger roundCoin = roundCoin(u.getTargetValue(), t);
		for (long i = 0; i < roundCoin.longValue(); i++) {

			addresses.add(u.getBeneficiaryAddress());
		}

		return addresses;
	}

	/*
	 * round without decimals
	 */

	private BigInteger roundCoin(BigInteger c, Token t) {

		return c.divide(BigInteger.valueOf(LongMath.checkedPow(10, t.getDecimals())));

	}

	public BigInteger sum() {
		BigInteger sum = BigInteger.ZERO;
		for (ContractEventInfo u : userUtxos) {
			sum = sum.add(u.getTargetValue());
		}
		return sum;
	}

	/*
	 * condition for execute the lottery 1) no other pending payment 2) can do the
	 * send failed block again 3) the sum is ok
	 */
	private boolean canTakeWinner(List<ContractEventInfo> player, List<ContractEventInfo> userlist) {

		BigInteger sum = BigInteger.ZERO;
		for (ContractEventInfo u : player) {

			sum = sum.add(u.getTargetValue());
			userlist.add(u);
			if (sum.compareTo(winnerAmount) >= 0) {
				return macthed = true;
			}

		}
		log.debug(" sum= " + sum);
		return macthed = false;

	}

	public synchronized Block batchGiveMoneyToECKeyList(String address, BigInteger amount, String memo,
			List<ContractEventInfo> userlist)
			throws JsonProcessingException, IOException, InsufficientMoneyException, UTXOProviderException, Exception {

		return walletAdmin.contractExecution(tokenid, amount, address, userlist, memo);
	}

	// get balance for the walletKeys
	protected List<ContractEventInfo> getContractEventInfo(String contractTokenid) throws Exception {
		List<ContractEventInfo> listUTXO = new ArrayList<ContractEventInfo>();
		List<String> keyStrHex000 = new ArrayList<String>();

		  byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getContractEvents.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetContractEventInfoResponse getBalancesResponse = Json.jsonmapper().readValue(response,
				GetContractEventInfoResponse.class);

		// no pending utxo
		for (ContractEventInfo utxo : getBalancesResponse.getOutputs()) {
			if (utxo.getTargetValue().signum() > 0 || utxo.getTargetTokenid().equals(tokenid)) {

				listUTXO.add(utxo);
			}
		}

		return listUTXO;
	}

	public String getTokenid() {
		return tokenid;
	}

	public void setTokenid(String tokenid) {
		this.tokenid = tokenid;
	}

	public NetworkParameters getParams() {
		return params;
	}

	public void setParams(NetworkParameters params) {
		this.params = params;
	}

	public String getContext_root() {
		return contextRoot;
	}

	public void setContext_root(String context_root) {
		this.contextRoot = context_root;
	}

	public String getWinner() {
		return winner;
	}

	public void setWinner(String winner) {
		this.winner = winner;
	}

	public String getContextRoot() {
		return contextRoot;
	}

	public void setContextRoot(String contextRoot) {
		this.contextRoot = contextRoot;
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

	public Wallet getWalletAdmin() {
		return walletAdmin;
	}

	public void setWalletAdmin(Wallet walletAdmin) {
		this.walletAdmin = walletAdmin;
	}

	public List<ContractEventInfo> getUserUtxos() {
		return userUtxos;
	}

	public void setUserUtxos(List<ContractEventInfo> userUtxos) {
		this.userUtxos = userUtxos;
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
