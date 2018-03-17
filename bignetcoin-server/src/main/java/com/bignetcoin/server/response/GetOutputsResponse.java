/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.response;

import java.util.List;

import org.bitcoinj.core.UTXO;

public class GetOutputsResponse extends AbstractResponse {

    private List<UTXO> outputs;
    
    public static AbstractResponse create(List<UTXO> outputs) {
        GetOutputsResponse res = new GetOutputsResponse();
        res.outputs = outputs;
        return res;
    }

    public List<UTXO> getOutputs() {
        return outputs;
    }
}
