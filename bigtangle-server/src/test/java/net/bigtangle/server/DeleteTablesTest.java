/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.NetworkParameters;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DeleteTablesTest extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;

    @Before
    public void setUp() throws Exception {
        store= storeService.getStore();
    }

    @Test
    // init
    public void deleteStore() throws Exception {
        store.deleteStore();
    }

    @Test
    @Ignore
    // must fix for testnet and mainnet
    public void testGenesisBlockHash() throws Exception {
        assertTrue(networkParameters.getGenesisBlock().getHash().toString()
                .equals("f3f9fbb12f3a24e82f04ed3f8afe1dac7136830cd953bd96b25b1371cd11215c"));

    }
}
