/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.Test;

import net.bigtangle.core.PeerAddress;
import net.bigtangle.core.Utils;
import net.bigtangle.params.MainNetParams;

import java.net.InetAddress;

import static net.bigtangle.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class PeerAddressTest
{
    @Test
    public void testPeerAddressRoundtrip() throws Exception {
        // copied verbatim from https://en.bitcoin.it/wiki/Protocol_specification#Network_address
        String fromSpec = "010000000000000000000000000000000000ffff0a000001208d";
        PeerAddress pa = new PeerAddress(MainNetParams.get(),
                HEX.decode(fromSpec), 0, 0);
        String reserialized = Utils.HEX.encode(pa.unsafeBitcoinSerialize());
        assertEquals(reserialized,fromSpec );
    }

    @Test
    public void testBitcoinSerialize() throws Exception {
        PeerAddress pa = new PeerAddress(InetAddress.getByName(null), 8333, 0);
        assertEquals("000000000000000000000000000000000000ffff7f000001208d",
                Utils.HEX.encode(pa.bitcoinSerialize()));
    }
}
