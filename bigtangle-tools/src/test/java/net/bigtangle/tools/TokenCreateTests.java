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
import net.bigtangle.core.Utils;

public class TokenCreateTests extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TokenCreateTests.class);

    //18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    
    @Test
    public void testYuanToken() throws JsonProcessingException, Exception {
     
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(yuanTokenPriv),
                Utils.HEX.decode(yuanTokenPub)),  "人民币",2,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(BTCTokenPriv),
                Utils.HEX.decode(BTCTokenPub)),  "BTC",8,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(ETHTokenPriv),
                Utils.HEX.decode(ETHTokenPub)),  "ETH",8,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(EURTokenPriv),
                Utils.HEX.decode(EURTokenPub)),  "EUR",2,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(USDTokenPriv),
                Utils.HEX.decode(USDTokenPub)),  "USD",2,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(JPYTokenPriv),
                Utils.HEX.decode(JPYTokenPub)),  "JPY",2,null);
        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(GOLDTokenPriv),
                Utils.HEX.decode(GOLDTokenPub)),  "GOLD",0,null);
    }
   // @Test
    public void testCreateToken() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
      //  while (true) { 
       
        wallet1();
     //   wallet2();
     //   createToken(walletAppKit1.wallet().walletKeys(null), "");
        createToken(walletAppKit1.wallet().walletKeys(null), "");
     //   createToken(walletAppKit2.wallet().walletKeys(null), "test-2-");
     //   }
    }

    private void createToken(List<ECKey> keys, String pre) throws JsonProcessingException, Exception {
        // String pre="test-1-" ;
        testCreateMultiSigToken(keys.get(1), pre + "Gold",0,null);
        testCreateMultiSigToken(keys.get(2), pre + "BTC",8,null);
        testCreateMultiSigToken(keys.get(3), pre + "ETH",8,null);
        // testCreateMultiSigToken(keys.get(4), "CNY");
     //   testCreateMultiSigToken(keys.get(7), pre + "人民币",2);
        testCreateMultiSigToken(keys.get(5), pre + "USD",2,null);
        testCreateMultiSigToken(keys.get(6), pre + "EUR",2,null);
    }

}
