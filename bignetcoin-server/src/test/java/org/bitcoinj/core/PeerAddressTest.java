/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import java.net.InetAddress;

import static org.bitcoinj.core.Utils.HEX;
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
