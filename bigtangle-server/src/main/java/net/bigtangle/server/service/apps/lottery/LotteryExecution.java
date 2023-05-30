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
	private List<UTXO> userUtxos;
	private BigInteger winnerAmount;
	private boolean macthed;
	private String contractTokenid;
	List<String> userAddress;

	public void execute() throws Exception {
		FullBlockStore store = storeService.getStore();

		try {
			List<UTXO> player = calUTXo(contractTokenid,store);
			userUtxos = new ArrayList<UTXO>();
			if (canTakeWinner(player, userUtxos, store)) {
				doTakeWinner(store);
			}
		} finally {
			store.close();
		}
	}

 
	private void doTakeWinner(FullBlockStore store) throws Exception {
		Token t = store.getTokenID(tokenid).get(0); 

		userAddress = baseList(userUtxos, t);
		  Block rollingBlock = blockService.getBlockPrototype(store);
		Block r1 = blockService.getBlockPrototype(store);
		// Deterministic randomization
		byte[] randomness = Utils.xor(r1.getPrevBlockHash().getBytes(), r1.getPrevBranchBlockHash().getBytes());
		SecureRandom se = new SecureRandom(randomness);

		winner = userAddress.get(se.nextInt(userAddress.size()));

		log.debug("winner " + winner + " sum =" + sum() + " \n user address size: " + userAddress.size());

	// no sign for contract	Block b = batchGiveMoneyToECKeyList(winner, sum(), "win lottery", userUtxos);

	//	log.debug("block " + (b == null ? "block is null" : b.toString()));

	}


    /*
     * split the list for lottery pay
     */
    private List<String> baseList(List<UTXO> player, Token t) {
        List<String> addresses = new ArrayList<String>();
        for (UTXO u : player) {
            addresses.addAll(baseList(u, t));
        }
        return addresses;
    }
    private List<String> baseList(UTXO u, Token t) {
        List<String> addresses = new ArrayList<String>();
        if (checkUTXO(u)) {
            long roundCoin = roundCoin(u.getValue(), t);
            for (long i = 0; i < roundCoin; i++) {

                addresses.add(u.getFromaddress());
            }
        }
        return addresses;
    }

  
    /*
     * round without decimals
     */

    private long roundCoin(Coin c, Token t) {

        return LongMath.divide(c.getValue().longValue(), LongMath.checkedPow(10, t.getDecimals()), RoundingMode.DOWN);

    }

    public BigInteger sum() {
        BigInteger sum = BigInteger.ZERO;
        for (UTXO u : userUtxos) {
            sum = sum.add(u.getValue().getValue());
        }
        return sum;
    }

    /*
     * condition for execute the lottery 1) no other pending payment 2) can do
     * the send failed block again 3) the sum is ok
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
	private boolean canTakeWinner(List<UTXO> player, List<UTXO> userlist, FullBlockStore store) {

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

	private boolean checkUTXO(UTXO u) {
		return u.getFromaddress() != null && !"".equals(u.getFromaddress())
				&& !u.getFromaddress().equals(u.getAddress());
	}

 
	// get balance for the walletKeys
	protected List<UTXO> calUTXo(String contractTokenid, FullBlockStore store) throws Exception {
		ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(tokenid));
		Address address = ecKey.toAddress(networkParameters);
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		// no pending utxo
		for (UTXO utxo : store.getOpenTransactionOutputs(address.toString())) {
			if (utxo.getValue().getValue().signum() > 0 || utxo.getTokenId().equals(tokenid)) {
				if (!utxo.isSpendPending())
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
