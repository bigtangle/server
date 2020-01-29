/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class Lottery {

  //  private static final Logger log = LoggerFactory.getLogger(Lottery.class);
    private String tokenid;
    public String context_root = "http://localhost:8088/";
    private Wallet walletAdmin;
    private NetworkParameters params;
    private String winner;
    private List<UTXO> userUtxos;
    private BigInteger winnerAmount;
    private boolean macthed;
    private ECKey accountKey;
    /*
     * start check balance and check to X amount and collect all user in lottery
     * list of (each ticket, address) compute random selection of winner pay to
     * winner address
     */
    public void start() throws Exception {
      //  ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(tokenid));
        List<UTXO> player = getBalance(accountKey);
        userUtxos = new ArrayList<UTXO>();
        if (canTakeWinner(player, userUtxos)) {
            doTakeWinner();
        }
    }

    private void doTakeWinner() throws Exception {
        Token t = walletAdmin.checkTokenId(tokenid);

        List<String> userAddress = baseList(userUtxos, t);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] buf = OkHttp3Util.postAndGetBlock(context_root + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block r1 = params.getDefaultSerializer().makeBlock(buf);
        // Deterministic randomization
        byte[] randomness = Utils.xor(r1.getPrevBlockHash().getBytes(), r1.getPrevBranchBlockHash().getBytes());
        SecureRandom se = new SecureRandom(randomness);

        winner = userAddress.get(se.nextInt(userAddress.size()));

        batchGiveMoneyToECKeyList(winner, sum(), "win lottery", userUtxos);
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

    private boolean checkUTXO(UTXO u) {
        return u.getFromaddress() != null && !"".equals(u.getFromaddress())
                && !u.getFromaddress().equals(u.getAddress());
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

    private boolean canTakeWinner(List<UTXO> player,   List<UTXO> userlist) {

        BigInteger sum = BigInteger.ZERO;
        for (UTXO u : player) {
            if (checkUTXO(u)) {
                sum = sum.add(u.getValue().getValue());
                userlist.add(u);
                if (sum.compareTo(winnerAmount) >= 0) {
                    return macthed= true;
                }
            }
        }
        return macthed=false;

    }

    /*
     * TODO To enable parallel payment, we should use different from address
     */
    public synchronized Block batchGiveMoneyToECKeyList( String address, BigInteger amount, String memo,
            List<UTXO> userlist)
            throws JsonProcessingException, IOException, InsufficientMoneyException, UTXOProviderException, Exception {
    	
        return walletAdmin.payFromList(null, address , new Coin(amount,Utils.HEX.decode(tokenid)), memo,
                userlist);

    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String response = OkHttp3Util.post(context_root + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        // String response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    protected List<UTXO> getBalance(ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return getBalance(keys);
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public String getCONTEXT_ROOT() {
        return context_root;
    }

    public void setCONTEXT_ROOT(String cONTEXT_ROOT) {
        context_root = cONTEXT_ROOT;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public void setParams(NetworkParameters params) {
        this.params = params;
    }

    public Wallet getWalletAdmin() {
    	  return walletAdmin;
     }

    public void setWalletAdmin(Wallet walletAdmin) {
        this.walletAdmin = walletAdmin;
    }    
 

    public String getContext_root() {
        return context_root;
    }

    public void setContext_root(String context_root) {
        this.context_root = context_root;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public List<UTXO> getUserUtxos() {
        return userUtxos;
    }

    public void setUserUtxos(List<UTXO> userUtxos) {
        this.userUtxos = userUtxos;
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

    public ECKey getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(ECKey accountKey) {
        this.accountKey = accountKey;
    }

}
