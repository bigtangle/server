/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class TokenCreateTests3 extends HelpTest {

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {
 
        walletAppKit1.wallet().setServerURL("https://p.bigtangle.de:8088/");
        walletAppKit1.wallet().getDomainNameBlockHash("test.shop") ;
  
    }
 

}
