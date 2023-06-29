/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.bigtangle.core.NetworkParameters;

 
public class DeleteTablesTest extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;

    @BeforeEach
    public void setUp() throws Exception {
        store= storeService.getStore();
    }

    @Test
    // init
    public void deleteStore() throws Exception {
        store.deleteStore();
    }

    @Test
    @Disabled
    // must fix for testnet and mainnet
    public void testGenesisBlockHash() throws Exception {
        assertTrue(networkParameters.getGenesisBlock().getHash().toString()
                .equals("f3f9fbb12f3a24e82f04ed3f8afe1dac7136830cd953bd96b25b1371cd11215c"));

    }
}
