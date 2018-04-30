/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.net.discovery;

import org.junit.Test;

import net.bigtangle.net.discovery.SeedPeers;
import net.bigtangle.params.MainNetParams;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class SeedPeersTest {
    @Test
    public void getPeer_one() throws Exception{
        SeedPeers seedPeers = new SeedPeers(MainNetParams.get());
        assertThat(seedPeers.getPeer(), notNullValue());
    }
    
    @Test
    public void getPeer_all() throws Exception{
        SeedPeers seedPeers = new SeedPeers(MainNetParams.get());
        for (int i = 0; i < MainNetParams.get().getAddrSeeds().length; ++i) {
            assertThat("Failed on index: "+i, seedPeers.getPeer(), notNullValue());
        }
        assertThat(seedPeers.getPeer(), equalTo(null));
    }
    
    @Test
    public void getPeers_length() throws Exception{
        SeedPeers seedPeers = new SeedPeers(MainNetParams.get());
        InetSocketAddress[] addresses = seedPeers.getPeers(0, 0, TimeUnit.SECONDS);
        assertThat(addresses.length, equalTo(MainNetParams.get().getAddrSeeds().length));
    }
}
