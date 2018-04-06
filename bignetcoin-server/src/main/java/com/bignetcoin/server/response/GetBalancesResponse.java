/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.response;

import java.util.List;

import net.bigtangle.core.Coin;
import net.bigtangle.core.UTXO;

public class GetBalancesResponse extends AbstractResponse {

    private List<UTXO> outputs;
    
    private List<Coin> tokens;
    
    public static AbstractResponse create(List<Coin> tokens, List<UTXO> outputs) {
        GetBalancesResponse res = new GetBalancesResponse();
        res.outputs = outputs;
        res.tokens = tokens;
        return res;
    }

    public List<UTXO> getOutputs() {
        return outputs;
    }

    public List<Coin> getTokens() {
        return tokens;
    }
}
