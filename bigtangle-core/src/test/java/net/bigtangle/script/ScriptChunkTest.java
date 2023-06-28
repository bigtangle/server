/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.script;

import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA1;
import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA2;
import static net.bigtangle.script.ScriptOpCodes.OP_PUSHDATA4;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ScriptChunkTest {

	@Test
	public void testShortestPossibleDataPush() {
		assertTrue(new ScriptBuilder().data(new byte[0]).build().getChunks().get(0).isShortestPossiblePushData(),
				"empty push");

		for (byte i = -1; i < 127; i++)
			assertTrue(
					new ScriptBuilder().data(new byte[] { i }).build().getChunks().get(0).isShortestPossiblePushData(),
					"push of single byte " + i);

		for (int len = 2; len < Script.MAX_SCRIPT_ELEMENT_SIZE; len++)
			assertTrue(new ScriptBuilder().data(new byte[len]).build().getChunks().get(0).isShortestPossiblePushData(),
					"push of " + len + " bytes");

		// non-standard chunks
		for (byte i = 1; i <= 16; i++)
			assertFalse(new ScriptChunk(1, new byte[] { i }).isShortestPossiblePushData(), "push of smallnum " + i);
		assertFalse(new ScriptChunk(OP_PUSHDATA1, new byte[75]).isShortestPossiblePushData(), "push of 75 bytes");
		assertFalse(new ScriptChunk(OP_PUSHDATA2, new byte[255]).isShortestPossiblePushData(), "push of 255 bytes");
		assertFalse(new ScriptChunk(OP_PUSHDATA4, new byte[65535]).isShortestPossiblePushData(), "push of 65535 bytes");
	}
}
