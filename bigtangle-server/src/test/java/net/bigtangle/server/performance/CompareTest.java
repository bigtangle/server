/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.TXReward;
import net.bigtangle.core.UTXO;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.server.service.SyncBlockService.Tokensums;

 //@Ignore
public class CompareTest {
    public static boolean testnet = true;
    public static String HTTPS_BIGTANGLE_DE = "https://" + (testnet ? "test." : "") + "bigtangle.de:8089/";
    public static String HTTPS_BIGTANGLE_INFO =
            // HTTPS_BIGTANGLE_LOCAL;
            "https://" + (testnet ? "test." : "") + "bigtangle.info:8089/";
    public static String HTTPS_BIGTANGLE_ORG = "https://" + (testnet ? "test." : "") + "bigtangle.org:8089/";
    public static String HTTPS_BIGTANGLE_LOCAL = "http://" + "localhost:8088/";

    public static String TESTSERVER1 = HTTPS_BIGTANGLE_DE;

    public static String TESTSERVER2 = HTTPS_BIGTANGLE_ORG;
    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    SyncBlockService syncBlockService;

    @Test
    public void diffThread() throws Exception {
        // System.setProperty("https.proxyHost",
        // "anwproxy.anwendungen.localnet.de");
        // System.setProperty("https.proxyPort", "3128");

        syncBlockService = new SyncBlockService();

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
        log.debug("\n " + TESTSERVER2 + "txreward  " + txreward2.size() + "\n "
                + txreward2.get(txreward2.size() - 1).getBlockHash() + "\n " + TESTSERVER1 + "   txreward  "
                + txreward.size() + "\n " + txreward.get(txreward.size() - 1).getBlockHash());
        // log.debug(txreward2.toString());
        assertTrue("Math.abs(txreward2.size " + txreward2.size() + " - txreward.size{} ) < 20" + txreward.size(),
                Math.abs(txreward2.size() - txreward.size()) < 20);

        Map<String, Tokensums> r1 = result.get(TESTSERVER1);
        Map<String, Tokensums> r2 = result.get(TESTSERVER2);
        for (Entry<String, Tokensums> a : r1.entrySet()) {
            Tokensums t1 = a.getValue();
            assertTrue(TESTSERVER1 + " " + t1.toString(), t1.check());
            Tokensums t = r2.get(a.getKey());
            assertTrue(TESTSERVER2 + " " + t.toString(), t.check());

            compareUTXO(t1, t);
            compareUTXO(t, t1);

            if (txreward2.size() == txreward.size()) {
                assertTrue("\n " + TESTSERVER1 + ": " + t1.toString() + "\n " + TESTSERVER2 + ": " + t,
                        t1.equals(t) || t1.unspentOrderSum().equals(t.unspentOrderSum()));

            }
        }

        if (txreward2.size() == txreward.size())
            log.debug(" no difference \n" + r1.toString() + " \n " + r2.toString());

    }

    private void compareUTXO(Tokensums t1, Tokensums t) {

        log.debug("\n " + t1.toString() + "\n " + TESTSERVER1 + " utxo size : " + t1.getUtxos().size() + "\n "
                + TESTSERVER2 + ": " + t.getUtxos().size());

        for (Entry<String, UTXO> a : t1.getUtxos().entrySet()) {
            UTXO find = t.getUtxos().get(a.getKey());
            if (find == null) {
                List<UTXO> utxos1 = findFromBlockHash(t1, a.getValue().getBlockHashHex());
                List<UTXO> utxos2 = findFromBlockHash(t, a.getValue().getBlockHashHex());
                log.error("\n " + " not found " + a.getValue().toString());
                log.error("\n " + utxos1);
                log.error("\n " + utxos2);
            } else {
                if (!a.getValue().getValue().equals(find.getValue()))
                    log.error("\n " + TESTSERVER1 + ": " + a.getValue().getValue().toString() + "\n " + TESTSERVER2
                            + ": " + find.getValue().toString());
            }
        }
    }

    private List<UTXO> findFromBlockHash(Tokensums t1, String blockHashHex) {
        List<UTXO> res = new ArrayList<>();
        for (Entry<String, UTXO> a : t1.getUtxos().entrySet()) {
            if (a.getKey().startsWith(blockHashHex))
                res.add(a.getValue());
        }
        return res;
    }

}