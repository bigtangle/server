/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.UTXO;
import net.bigtangle.core.http.AbstractResponse;

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
