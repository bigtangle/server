/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.math.BigInteger;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;

public class PartsToOne extends HelpTest {

    private static final Logger log = LoggerFactory.getLogger(PartsToOne.class);

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        setUp();
        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());
        
       Block b= walletAppKit1.wallet().payPartsToOne(null, walletAppKit1.wallet().walletKeys().get(0).toAddress(networkParameters), NetworkParameters.BIGTANGLE_TOKENID,
                "parts to one", new BigInteger("200000000"));
       log.debug("  " +b.toString() );
       Block b2= walletAppKit2.wallet().payPartsToOne(null, walletAppKit2.wallet().walletKeys().get(0).toAddress(networkParameters), NetworkParameters.BIGTANGLE_TOKENID,
                "parts to one",  new BigInteger("200000000"));
        log.debug("  " +b2.toString() );
    }
   
}
