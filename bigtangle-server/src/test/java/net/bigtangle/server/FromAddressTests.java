/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class FromAddressTests extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;
    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

    private ECKey accountKey;
    Wallet w;
    protected static final Logger log = LoggerFactory.getLogger(FromAddressTests.class);

    @Test 
    public void testUserpay() throws Exception {
        w = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.setServerURL(contextRoot);
        accountKey = new ECKey(); 
        testTokens();
        makeRewardBlock();
        createUserPay(accountKey);

    }

    private void checkResult(ECKey userkey, String fromaddress, String memo) throws Exception {

        List<UTXO> users = getBalance(userkey.toAddress(networkParameters).toBase58());

        for (UTXO u : users) {
            assertTrue(u.getFromaddress().equals(fromaddress));
            assertTrue(u.getMemoInfo().getKv().get(0).getValue().equals(memo));
        }
    }

    private void createUserPay(ECKey accountKey) throws Exception {
        List<ECKey> ulist = payKeys();
        for (ECKey key : ulist) {
            buyTicket(key, accountKey);
        }

    }

    /*
     * pay money to the key and use the key to buy lottery
     */
    public void buyTicket(ECKey key, ECKey accountKey) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, key);
        w.setServerURL(contextRoot);
        try {

            w.pay(null, accountKey.toAddress(networkParameters), Coin.valueOf(100, Utils.HEX.decode(yuanTokenPub)),
                    " buy ticket");
        } catch (InsufficientMoneyException e) {
            // TODO: handle exception
        }
        mcmc();
   //     checkResult(accountKey, key.toAddress(networkParameters).toBase58());
    }

    public List<ECKey> payKeys() throws Exception {
        List<ECKey> userkeys = new ArrayList<ECKey>();
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        ECKey key = new ECKey();
        giveMoneyResult.put(key.toAddress(networkParameters).toString(), 100l);
        userkeys.add(key);
        ECKey key2 = new ECKey();
        giveMoneyResult.put(key2.toAddress(networkParameters).toString(), 100l);
        userkeys.add(key2);
        
        String memo = "pay to user";
        Block b = w.payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), memo );
        log.debug("block " + (b == null ? "block is null" : b.toString()));
        mcmc();

        checkResult(key, w.walletKeys().get(0).toAddress(networkParameters).toBase58(),memo);
        return userkeys;
    }



    public void testTokens() throws JsonProcessingException, Exception {
        String domain = "";
        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)), "人民币", 2, domain, "人民币 CNY",
                BigInteger.valueOf(10000000l));
        makeRewardBlock();
    }

    public Address getAddress() {
        return ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).toAddress(networkParameters);
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
            String description, BigInteger amount) throws JsonProcessingException, Exception {
        try {
            walletAppKit1.wallet().setServerURL(contextRoot);
             createToken(key, tokename, decimals, domainname, description, amount, true, null,  TokenType.identity.ordinal(), key.getPublicKeyAsHex(),
                    walletAppKit1.wallet());
            ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

            walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);

        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(String address) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            listUTXO.add(utxo);
        }

        return listUTXO;
    }

}
