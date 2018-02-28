/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.script;

import static org.bitcoinj.script.ScriptOpCodes.OP_PUSHDATA1;
import static org.bitcoinj.script.ScriptOpCodes.OP_PUSHDATA2;
import static org.bitcoinj.script.ScriptOpCodes.OP_PUSHDATA4;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScriptChunkTest {

    @Test
    public void testShortestPossibleDataPush() {
        assertTrue("empty push", new ScriptBuilder().data(new byte[0]).build().getChunks().get(0)
                .isShortestPossiblePushData());

        for (byte i = -1; i < 127; i++)
            assertTrue("push of single byte " + i, new ScriptBuilder().data(new byte[] { i }).build().getChunks()
                    .get(0).isShortestPossiblePushData());

        for (int len = 2; len < Script.MAX_SCRIPT_ELEMENT_SIZE; len++)
            assertTrue("push of " + len + " bytes", new ScriptBuilder().data(new byte[len]).build().getChunks().get(0)
                    .isShortestPossiblePushData());

        // non-standard chunks
        for (byte i = 1; i <= 16; i++)
            assertFalse("push of smallnum " + i, new ScriptChunk(1, new byte[] { i }).isShortestPossiblePushData());
        assertFalse("push of 75 bytes", new ScriptChunk(OP_PUSHDATA1, new byte[75]).isShortestPossiblePushData());
        assertFalse("push of 255 bytes", new ScriptChunk(OP_PUSHDATA2, new byte[255]).isShortestPossiblePushData());
        assertFalse("push of 65535 bytes", new ScriptChunk(OP_PUSHDATA4, new byte[65535]).isShortestPossiblePushData());
    }
}
