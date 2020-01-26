/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

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
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.TestParams;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class StartLottery {

    private static final Logger log = LoggerFactory.getLogger(StartLottery.class);
    private String tokenid;
    public String CONTEXT_ROOT = "http://localhost:8088/";
    private WalletAppKit walletAdmin;
    public NetworkParameters params = TestParams.get();

    // "http://localhost:8088/";//
    public static void main(String[] args) throws Exception {

        StartLottery startLottery = new StartLottery();
        if (args.length > 0)
            startLottery.CONTEXT_ROOT = args[0];

    }

    /*
     * start check balance and check to X amount and collect all user in lottery
     * list of (each ticket, address) compute random selection of winner pay to
     * winner address
     */
    public void start() throws Exception {
        ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(tokenid));
        // setwallet();

        List<UTXO> player = getBalance(ecKey);
        BigInteger winnerAmount = new BigInteger("1000");
        List<UTXO> userUtxos = new ArrayList<UTXO>();
        if (canTakeWinner(player, winnerAmount, userUtxos)) {
            doTakeWinner(userUtxos);
        }
    }

    private void doTakeWinner(List<UTXO> userUtxos) throws Exception {
        Token t = walletAdmin.wallet().checkTokenId(tokenid);

        List<String> userAddress = baseList(userUtxos, t);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] buf = OkHttp3Util.postAndGetBlock(CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block r1 = params.getDefaultSerializer().makeBlock(buf);
        // Deterministic randomization
        byte[] randomness = Utils.xor(r1.getPrevBlockHash().getBytes(), r1.getPrevBranchBlockHash().getBytes());
        SecureRandom se = new SecureRandom(randomness);

        String winner = userAddress.get(se.nextInt(userAddress.size()));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(winner, sum(userUtxos).longValue());
        batchGiveMoneyToECKeyList(giveMoneyResult, "win lottery", userUtxos);
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

    private BigInteger sum(List<UTXO> player) {
        BigInteger sum = BigInteger.ZERO;
        for (UTXO u : player) {
            sum = sum.add(u.getValue().getValue());
        }
        return sum;
    }
    private boolean canTakeWinner(List<UTXO> player, BigInteger winnerAmount, List<UTXO> userlist) {

        BigInteger sum = BigInteger.ZERO;
        for (UTXO u : player) {
            if (checkUTXO(u)) {
                sum = sum.add(u.getValue().getValue());
                userlist.add(u);
                if (sum.compareTo(winnerAmount) >= 0) {
                    return true;
                }
            }
        }
        return false;

    }

    /*
     * TODO To enable parallel payment, we should use different from address
     */
    public synchronized Block batchGiveMoneyToECKeyList(HashMap<String, Long> giveMoneyResult, String memo, 
            List<UTXO> userlist)
            throws JsonProcessingException, IOException, InsufficientMoneyException, UTXOProviderException, Exception {

        return walletAdmin.wallet().payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(tokenid),memo,userlist);

    }

    protected void setwallet() throws Exception {
        KeyParameter aesKey = null;
        walletAdmin = new WalletAppKit(params, new File("/home/cui/Downloads"), "201811210100000002");
        walletAdmin.wallet().setServerURL(CONTEXT_ROOT);
        importKeys(walletAdmin.wallet());
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String response = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getBalances.name(),
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
        return CONTEXT_ROOT;
    }

    public void setCONTEXT_ROOT(String cONTEXT_ROOT) {
        CONTEXT_ROOT = cONTEXT_ROOT;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public void setParams(NetworkParameters params) {
        this.params = params;
    }

    public WalletAppKit getWalletAdmin() {
        return walletAdmin;
    }

    public void setWalletAdmin(WalletAppKit walletAdmin) {
        this.walletAdmin = walletAdmin;
    }

    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

    // 1GDsvV5Vgwa7VYULyDmW9Unj9v1hWFxBJ5
    public static String USDTokenPub = "02fbef0f3e1344f548abb7d4b6a799e372d2310ff13fe023b3ba0446f2e3f58e04";
    public static String USDTokenPriv = "6fb4cb30d593536e3c54ac17bfaa311cb0e2bdf219c89483aa8d7185f6c5c3b7";

    // 1FM6YGALJNTUmdoofxW6Rw5C2jW6AESEEs
    public static String EURTokenPub = "03163532b12879ff2f52e84ec032662f5d0ee0eee33355d9c0a833d172d3d3e4cb";
    public static String EURTokenPriv = "52bd881f650733448b20358ca4ab9bdbf394c150948c81eedc1b666c1c47f61f";

    // 1GZH9mf9w9K3nc58xR3dpTJUuJdiLnuwdW
    public static String ETHTokenPub = "02b8b21c6341872dda1f3a4f26d0d887283ad99f342d1dc35552db39c830919722";
    public static String ETHTokenPriv = "9eac170431a4c8cb188610cea2d40a3db5f656db5b52c0ac5aa9aa3a3fa8366f";

    // 1PttyezdBEgrfB8ZUg8YtQoqtHoYJyyadS
    public static String BTCTokenPub = "0352ac6c7fe48bff55b6976e70718a5c37fe9ddf5541473284bff2c72f51fb60e2";
    public static String BTCTokenPriv = "d82d565e4ad9e2c78610535cbe0a37d2e240192bf1d7f42f4276cd89351f45d0";

    // 13YjgF6Wa3v4i6NQQqn94XCg6CX79NVgWe
    public static String JPYTokenPub = "03e608ba3cbce11acc4a6b5e0b63b3381af7e9f50c1d43e6a6ce8cf16d3743891c";
    public static String JPYTokenPriv = "7a62e210952b6d49d545edb6fe4c322d68605c1b97102448a3439158ba9acd5f";

    // 1Q5ysrjmeEjJKFBrnEJBSAs6vBHYzsmN2H
    public static String ShopDomainPub = "02b5fb501bdb5ea68949f7fd37a7a75728ca3bdd4b0aacd1a6febc0c34a7338694";
    public static String ShopDomainPriv = "5adeeab95523100880b689fc9150650acca8c3a977552851bde75f85e1453bf2";

    public static String BigtangleDomainPub = "02122251e6e3cdbe3e4bbaa4bc0dcc12014c6cf0388abac61bf2c972579d790a68";
    public static String BigtangleDomainPriv = "dbee6582476dc44ac1e26c67733205ff4c50a1a6a6716667b4428b36f0dcb7bc";

    public static String DomainComPriv = "64a48e5a568e4498a51df1d35eced926b27d7bb29bfb0d4f6efb256c97381e07";

    public static String DomainComPub = "022d607a37d3d4467557a003189531a8198abb9967adec542edea70305b4785324";

    protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";

    public void importKeys(Wallet w) throws Exception {
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BTCTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ShopDomainPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BigtangleDomainPriv)));
    }
}
