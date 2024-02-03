package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.DataClassName;

public class ConstantTest {

    @Test
    public void blockType() {
        // This data is used to save data in database, so it can be never change
        // Only new add
        assertTrue(Block.Type.BLOCKTYPE_INITIAL.ordinal() == 0);
        assertTrue(Block.Type.BLOCKTYPE_TRANSFER.ordinal() == 1);
        assertTrue(Block.Type.BLOCKTYPE_REWARD.ordinal() == 2);
        assertTrue(Block.Type.BLOCKTYPE_TOKEN_CREATION.ordinal() == 3);
        assertTrue(Block.Type.BLOCKTYPE_USERDATA.ordinal() == 4);
        assertTrue(Block.Type.BLOCKTYPE_CONTRACT_EVENT.ordinal() == 5);
        assertTrue(Block.Type.BLOCKTYPE_GOVERNANCE.ordinal() == 6);
        assertTrue(Block.Type.BLOCKTYPE_FILE.ordinal() == 7);
        assertTrue(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE.ordinal() == 8);
        assertTrue(Block.Type.BLOCKTYPE_CROSSTANGLE.ordinal() == 9);
        assertTrue(Block.Type.BLOCKTYPE_ORDER_OPEN.ordinal() == 10);
        assertTrue(Block.Type.BLOCKTYPE_ORDER_CANCEL.ordinal() == 11);
        assertTrue(Block.Type.BLOCKTYPE_ORDER_EXECUTE.ordinal() == 12);
        assertTrue(Block.Type.BLOCKTYPE_CONTRACTEVENT_CANCEL.ordinal() == 13);
    }


   
    
}
