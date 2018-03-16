/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.response;

import java.util.HashMap;
import java.util.List;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.UTXO;

public class GetBalancesResponse extends AbstractResponse {

    private List<UTXO> outputs;
    
    private HashMap<Long, Coin> tokens;
    
    public static AbstractResponse create(HashMap<Long, Coin> tokens, List<UTXO> outputs) {
        GetBalancesResponse res = new GetBalancesResponse();
        res.outputs = outputs;
        res.tokens = tokens;
        return res;
    }

    public List<UTXO> getOutputs() {
        return outputs;
    }

    public HashMap<Long, Coin> getTokens() {
        return tokens;
    }
}
