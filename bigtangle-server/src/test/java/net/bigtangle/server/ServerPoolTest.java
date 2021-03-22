package net.bigtangle.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.params.MainNetParams;
import net.bigtangle.pool.server.ServerPool;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServerPoolTest extends AbstractIntegrationTest {
 
 

     @Test
    public void walletServerpool() throws Exception {

        walletAppKit.wallet().getServerURL();
        

    

    }

   //  @Test
    public void testServerpool() throws Exception {

       ServerPool s = new ServerPool(MainNetParams.get());
        s.serverSeeds();
      
        assertEquals( s.getServers().size(),3 );
        

    }
 

}
