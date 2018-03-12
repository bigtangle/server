/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.response;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Utils;

public class AskTransactionResponse extends AbstractResponse {

    private String blockHex;

    public static AbstractResponse create(String blockHex) {
        AskTransactionResponse res = new AskTransactionResponse();
        res.blockHex = blockHex;
        res.setDuration(0);
        return res;
    }
    
    public static AbstractResponse create(byte[] data) {
        AskTransactionResponse res = new AskTransactionResponse();
        res.blockHex = Utils.HEX.encode(data);
        res.setDuration(0);
        return res;
    }
    
    public static AbstractResponse create(Block block) {
        AskTransactionResponse res = new AskTransactionResponse();
        res.blockHex = Utils.HEX.encode(block.bitcoinSerialize());
        res.setDuration(0);
        return res;
    }

    public String getBlockHex() {
        return blockHex;
    }

    public void setBlockHex(String blockHex) {
        this.blockHex = blockHex;
    }
}
