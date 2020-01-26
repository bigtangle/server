/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.lottery.StartLottery;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LotteryTests extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;

    @Test
    public void lottery() throws Exception {
        walletAppKit1.wallet().importKey( ECKey.fromPrivate(Utils.HEX.decode(StartLottery.yuanTokenPriv)));
        
        testTokens();
        List<ECKey> ulist = payKeys();
        for (ECKey key : ulist) {
            buyTicket(key);
        }
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
        StartLottery startLottery = new StartLottery();
        startLottery.setTokenid(StartLottery.yuanTokenPub);
        startLottery.setCONTEXT_ROOT(contextRoot);
        startLottery. setParams(networkParameters);
        startLottery.setWalletAdmin( walletAppKit1);
        startLottery.start();
    }

    /*
     * pay money to the key and use the key to buy lottery
     */
    public void buyTicket(ECKey key) throws Exception {
        Wallet w = new Wallet(networkParameters);
        w.importKey(key);
        w.setServerURL(contextRoot);
        w.pay(null, getAddress(), Coin.valueOf(1000, Utils.HEX.decode(StartLottery.yuanTokenPub)), " buy ticket");
    }

    public List<ECKey> payKeys() throws Exception {
        List<ECKey> userkeys = new ArrayList<ECKey>();
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        for (int i = 0; i < 10; i++) {
            ECKey key = new ECKey();
            giveMoneyResult.put(key.toAddress(networkParameters).toString(), 10000l);
            userkeys.add(key);
        }
   
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult,
                Utils.HEX.decode(StartLottery.yuanTokenPub), "", 3, 20000);
        log.debug("block " + (b == null ? "block is null" : b.toString()));
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
        return userkeys;
    }

 

    public void testTokens() throws JsonProcessingException, Exception {

        String domain = "";

        testCreateMultiSigToken(
                ECKey.fromPrivate(Utils.HEX.decode(StartLottery.yuanTokenPriv)),
                "人民币", 2, domain, "人民币 CNY", new BigInteger("10000000000"));
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
    }

    public Address getAddress() {
        return ECKey.fromPrivate(Utils.HEX.decode(StartLottery.yuanTokenPriv)).toAddress(networkParameters);
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
            String description, BigInteger amount) throws JsonProcessingException, Exception {
        try {
            walletAppKit1.wallet().setServerURL(contextRoot);
            walletAppKit1.wallet().createToken(key, tokename, decimals, domainname, description, amount, true, null) ;

            ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv)  );

            walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);
         

        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }
}
