/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.compare;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.TXReward;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.server.service.SyncBlockService.Tokensums;

 
// @Ignore
public class CompareTest {
    public static boolean testnet = true;
    public static String HTTPS_BIGTANGLE_DE = "https://" + (testnet ? "test." : "") + "bigtangle.de/";
    public static String HTTPS_BIGTANGLE_INFO =
            // HTTPS_BIGTANGLE_LOCAL;
            "https://" + (testnet ? "test." : "") + "bigtangle.info/";
    public static String HTTPS_BIGTANGLE_ORG = "https://" + (testnet ? "test." : "") + "bigtangle.org/";
    public static String HTTPS_BIGTANGLE_LOCAL = "http://"  + "localhost:8088/";
    
    public static String TESTSERVER1 = HTTPS_BIGTANGLE_DE;

    public static String TESTSERVER2 = HTTPS_BIGTANGLE_LOCAL;
    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    SyncBlockService syncBlockService;
    
    @Test
    public void diffThread() throws Exception {

    	syncBlockService= new 	SyncBlockService();
    	
//        System.setProperty("https.proxyHost",
//         "anwproxy.anwendungen.localnet.de");
//        System.setProperty("https.proxyPort", "3128");

    	
        while (true) {
            try {
                testComapre();

                Thread.sleep(30000);
            } catch (Throwable e) {
                e.printStackTrace();

            }
        }

    }

    public void testComapre() throws Exception {

        List<TXReward> txreward = syncBlockService.getAllConfirmedReward(TESTSERVER1);
        List<TXReward> txreward2 = syncBlockService.getAllConfirmedReward(TESTSERVER2);
        Map<String, Map<String, Tokensums>> result = new HashMap<String, Map<String, Tokensums>>();

        syncBlockService.checkToken(TESTSERVER1, result);
        syncBlockService.checkToken(TESTSERVER2, result);
        log.debug(TESTSERVER2 + "txreward size " + txreward2.size() + TESTSERVER1 + "   txreward size "
                + txreward.size());
        // log.debug(txreward2.toString());
        assertTrue("Math.abs(txreward2.size " + txreward2.size() + " - txreward.size{} ) < 20" + txreward.size(),
                Math.abs(txreward2.size() - txreward.size()) < 20);
        // log.debug(result1.toString());
        // log.debug(result2.toString());

        Map<String, Tokensums> r1 = result.get(TESTSERVER1);
        Map<String, Tokensums> r2 = result.get(TESTSERVER2);
        for (Entry<String, Tokensums> a : r1.entrySet()) {
            Tokensums t1 = a.getValue();
            assertTrue(" " + t1.toString() , t1.check());
            Tokensums t = r2.get(a.getKey());
            assertTrue(" " + t.toString() ,t.check());
            if (txreward2.size() == txreward.size()) {
                 assertTrue("\n " + TESTSERVER1 + ": " + t1.toString() + "\n " + TESTSERVER2 + ": " + t, t1.equals(t)
                         || t1.unspentOrderSum() .equals(t.unspentOrderSum()));
               //  log.debug(" txreward2.size " + txreward2.size() + "  \n  txreward.size  " + txreward.size());
                 
            }
        }
        
        if (txreward2.size() == txreward.size())
        log.debug(" no difference \n" + r1.toString() 
        +" \n "+  r2.toString()); 
        // log.debug(TESTSERVER1 + " : " + r1.toString());
        // log.debug(TESTSERVER2 + " : " + r2.toString());

        // assertTrue(TESTSERVER1 +" : " + r1.toString() +TESTSERVER2+ " : ",
        // r1.equals(r2));
    }

}