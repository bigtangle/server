/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;

public class FundsTests extends HelpTest {

    private static final Logger log = LoggerFactory.getLogger(FundsTests.class);

 
    @Test
    public void testInitSellFunds() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        long initprice = 100;
        List<ECKey> keys = walletAppKit2.wallet().walletKeys(null);
 
      //  walletAppKit2.wallet().sellOrder(null, keys.get(1).getPublicKeyAsHex(), initprice, 1000, null,
      //          null);
        walletAppKit1.wallet().buyOrder(null, keys.get(1).getPublicKeyAsHex(), initprice, 1000, null,
                null);

    }
    
    @Test
        public void testBuyFunds() throws JsonProcessingException, Exception {
            // Setup transaction and signatures
            long initprice = 100;
             ECKey btc = walletAppKit1.wallet().walletKeys(null).get(2);
  
           
            walletAppKit2.wallet().buyOrder(null, btc.getPublicKeyAsHex(), initprice, 1000, null,
                    null);
            walletAppKit1.wallet().sellOrder(null,btc.getPublicKeyAsHex(), initprice, 1000, null,
                    null);


        // buy this order for this cryptofunds, funds get bc
         

        // funds buy other cryptos like BTC

        // calculate the price from the last price

        // for each token sum of alll
        // token price * amount
        // evaluation the funds every time

        /*
         * 0.00000001 ฿ = 1 Satoshi 0.00000100 ฿ = 1 μBTC (microbitcoin)
         * 0.00100000 ฿ = 1 mBTC (millibitcoin) 0.01000000 ฿ = 1 cBTC
         * (centibitcoin) 1.00000000 ฿ = 1 BTC (bitcoin)
         */
    }

}
