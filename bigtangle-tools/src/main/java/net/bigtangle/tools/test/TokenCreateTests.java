/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class TokenCreateTests extends HelpTest {

    private static final Logger log = LoggerFactory.getLogger(TokenCreateTests.class);

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(yuanTokenPriv),
                Utils.HEX.decode(yuanTokenPub)), "人民币", 2, null, "人民币 CNY");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(BTCTokenPriv), Utils.HEX.decode(BTCTokenPub)),
                "BTC", 8, null, "Bitcoin ETF");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(ETHTokenPriv), Utils.HEX.decode(ETHTokenPub)),
                "ETH", 8, null, "Ethereum ETF");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(EURTokenPriv), Utils.HEX.decode(EURTokenPub)),
                "EUR", 2, null,"Euro");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(USDTokenPriv), Utils.HEX.decode(USDTokenPub)),
                "USD", 2, null, "US Dollar");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(JPYTokenPriv), Utils.HEX.decode(JPYTokenPub)),
                "JPY", 2, null, "Japan Yuan");

    }
   
}
