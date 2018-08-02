/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.NetworkParameters;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ResetTest extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    // init
    public void testReset() throws Exception {
        store = dbConfiguration.store();
        store.resetStore();
    }

    @Test
    // must fix for testnet and mainnet
    public void testGenesisBlockHash() throws Exception {
        assertTrue(networkParameters.getGenesisBlock().getHash().toString()
                .equals("1f8e1cc52556af4cb7be5af3faf0a729dd2ac75b26c52459809b449fcaa6282a"));

    }
}
