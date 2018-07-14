/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.UTXO;
import net.bigtangle.core.http.AbstractResponse;

public class OutputsDetailsResponse extends AbstractResponse {

    private UTXO outputs;

    public static AbstractResponse create(UTXO outputs) {
        OutputsDetailsResponse res = new OutputsDetailsResponse();
        res.outputs = outputs;
        return res;
    }

    public UTXO getOutputs() {
        return outputs;
    }
}