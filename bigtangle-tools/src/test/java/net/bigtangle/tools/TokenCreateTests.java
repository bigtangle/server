/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;

public class TokenCreateTests extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TokenCreateTests.class);

    @Test
    public void testCreateToken() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        while (true) { 
       
        wallet1();
        wallet2();
        createToken(walletAppKit1.wallet().walletKeys(null), "test-1-");
        createToken(walletAppKit2.wallet().walletKeys(null), "test-2-");
        }
    }

    private void createToken(List<ECKey> keys, String pre) throws JsonProcessingException, Exception {
        // String pre="test-1-" ;
        testCreateMultiSigToken(keys.get(1), pre + "Gold");
        testCreateMultiSigToken(keys.get(2), pre + "BTC");
        testCreateMultiSigToken(keys.get(3), pre + "ETH");
        // testCreateMultiSigToken(keys.get(4), "CNY");
        testCreateMultiSigToken(keys.get(7), pre + "人民币");
        testCreateMultiSigToken(keys.get(5), pre + "USD");
        testCreateMultiSigToken(keys.get(6), pre + "EUR");
    }

}