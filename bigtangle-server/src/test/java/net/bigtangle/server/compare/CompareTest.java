/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.compare;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.TXReward;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.server.service.SyncBlockService.Tokensums;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 //@Ignore
public class CompareTest extends AbstractIntegrationTest { 
    public static boolean testnet = true;
    public static String HTTPS_BIGTANGLE_DE = "https://" + (testnet ? "test." : "") + "bigtangle.de/";
    public static String HTTPS_BIGTANGLE_INFO =
            // HTTPS_BIGTANGLE_LOCAL;
            "https://" + (testnet ? "test." : "") + "bigtangle.info/";
    public static String HTTPS_BIGTANGLE_ORG = "https://" + (testnet ? "test." : "") + "bigtangle.org/";

    public static String TESTSERVER1=   HTTPS_BIGTANGLE_ORG;
    
    public static String TESTSERVER2=   HTTPS_BIGTANGLE_DE;
    @Test
    public void testComapre() throws Exception { 
        
       List<TXReward> txreward = syncBlockService.getAllConfirmedReward(TESTSERVER1);
       List<TXReward> txreward2 = syncBlockService.getAllConfirmedReward(TESTSERVER2);
       Map<String,Set<Tokensums> > result1= new  HashMap<String,Set<Tokensums> >();
       Map<String,Set<Tokensums> > result2= new  HashMap<String,Set<Tokensums> >();
        syncBlockService.checkToken(TESTSERVER1, result1);
       syncBlockService.checkToken(TESTSERVER2, result2);
       log.debug(txreward.toString());
       log.debug(txreward2.toString());
       
       log.debug(result1.toString());
       log.debug(result2.toString());
       
    }
 
     
    
}