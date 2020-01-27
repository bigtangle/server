/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.lottery.Lottery;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LotteryTests extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;
    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";
    int usernumber =  Math.abs( new Random().nextInt())% 88;
    BigInteger winnerAmount = new BigInteger(Math.abs(new Random().nextInt())% 9999+"");
    
    private ECKey accountKey;
    
    protected static final Logger log = LoggerFactory.getLogger(LotteryTests.class);
    @Test
    public void lottery() throws Exception {
        for(int i=0; i<1;i++) {
              usernumber =  Math.abs( new Random().nextInt())% 88;
              winnerAmount = new BigInteger(Math.abs(new Random().nextInt())% 9999+"");

            lotteryDo();
            log.debug("done iteration " +i + "usernumber=" +usernumber +" winnerAmount="+ winnerAmount );
        }
    }
    public void lotteryDo() throws Exception {
        walletAppKit1.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        accountKey= new ECKey();
        walletAppKit1.wallet().importKey(accountKey);

        testTokens();
        createUserPay(accountKey);
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
        Lottery startLottery = startLottery();
        while(!startLottery.isMacthed()) {
            createUserPay(accountKey);
            startLottery = startLottery();
        }
        checkResult(startLottery);
    }

    private Lottery startLottery()
            throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
        Lottery startLottery = new Lottery();
        startLottery.setTokenid(yuanTokenPub);
        startLottery.setCONTEXT_ROOT(contextRoot);
        startLottery.setParams(networkParameters);
        startLottery.setWalletAdmin(walletAppKit1); 
        startLottery.setWinnerAmount(winnerAmount);
        startLottery.setAccountKey(accountKey);
        startLottery.start();
    
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
     return startLottery;
    }

    private void checkResult(Lottery startLottery) throws Exception {
        Coin coin = new Coin(startLottery.sum(), yuanTokenPub);

        List<UTXO> users = getBalance(startLottery.getWinner());
        UTXO myutxo = null;
        for (UTXO u : users) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue().equals(u.getValue().getValue())) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    private void createUserPay(ECKey accountKey) throws Exception {
        List<ECKey> ulist = payKeys();
        for (ECKey key : ulist) {
            buyTicket(key,accountKey);
        }
    }

    /*
     * pay money to the key and use the key to buy lottery
     */
    public void buyTicket(ECKey key, ECKey accountKey) throws Exception {
        Wallet w =   Wallet.fromKeys(networkParameters,key);
        w.setServerURL(contextRoot);
        try {
        int satoshis = Math.abs(new Random().nextInt())% 1000 ;
        w.pay(null, accountKey.toAddress(networkParameters), Coin.valueOf(satoshis, Utils.HEX.decode(yuanTokenPub)), " buy ticket");
        }catch (InsufficientMoneyException e) {
            // TODO: handle exception
        }
    }

    public List<ECKey> payKeys() throws Exception {
        List<ECKey> userkeys = new ArrayList<ECKey>();
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        for (int i = 0; i < usernumber; i++) {
            ECKey key = new ECKey();
            giveMoneyResult.put(key.toAddress(networkParameters).toString(), winnerAmount.longValue());
            userkeys.add(key);
        }

        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), "",
                3, 20000);
        log.debug("block " + (b == null ? "block is null" : b.toString()));
        sendEmpty(usernumber);
        mcmcService.update();
        confirmationService.update();
        return userkeys;
    }

    public void testTokens() throws JsonProcessingException, Exception {

        String domain = "";

        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)), "人民币", 2, domain, "人民币 CNY",
                winnerAmount.multiply(BigInteger.valueOf(usernumber * 10000l)));
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
    }

    public Address getAddress() {
        return ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).toAddress(networkParameters);
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
            String description, BigInteger amount) throws JsonProcessingException, Exception {
        try {
            walletAppKit1.wallet().setServerURL(contextRoot);
            walletAppKit1.wallet().createToken(key, tokename, decimals, domainname, description, amount, true, null);

            ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

            walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);

        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }
}
