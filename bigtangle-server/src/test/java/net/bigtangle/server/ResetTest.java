/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.store.FullPrunedBlockGraph;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ResetTest extends AbstractIntegrationTest { 
    @Autowired
    private NetworkParameters networkParameters;
    @Before
    public void setUp() throws Exception {
       
     }
    @Test
    //init 
    public void testReset() throws Exception {

        store = dbConfiguration.store();
        store.resetStore();

        blockgraph = new FullPrunedBlockGraph(networkParameters, store);

    }
}
