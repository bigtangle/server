/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.Test;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.VersionMessage;
import net.bigtangle.params.UnitTestParams;

import static net.bigtangle.core.Utils.HEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionMessageTest {
    @Test
    // Test that we can decode version messages which miss data which some old nodes may not include
    public void testDecode() throws Exception {
        NetworkParameters params = UnitTestParams.get();

        VersionMessage ver = new VersionMessage(params, HEX.decode("7111010000000000000000003334a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0004000000"));
        assertFalse(ver.relayTxesBeforeFilter);
        assertEquals(1024, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);

        ver = new VersionMessage(params, HEX.decode("711101000000000000000000a634a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0004000001"));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(1024, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);

        ver = new VersionMessage(params, HEX.decode("711101000000000000000000c334a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0000000001"));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(0, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);

        ver = new VersionMessage(params, HEX.decode("71110100000000000000000048e5e95000000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d0000000000000000"));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(0, ver.bestHeight);
        assertEquals("", ver.subVer);
    }
}
