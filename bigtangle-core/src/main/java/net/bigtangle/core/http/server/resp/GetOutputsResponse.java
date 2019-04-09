/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.http.AbstractResponse;

public class GetOutputsResponse extends AbstractResponse {

    private List<UTXO> outputs;

    private Map<String, Token> tokennames;
    
    public static AbstractResponse create(List<UTXO> outputs, Map<String, Token> tokennames) {
        GetOutputsResponse res = new GetOutputsResponse();
        res.outputs = outputs;
        res.tokennames = tokennames;
        return res;
    }

    public List<UTXO> getOutputs() {
        return outputs;
    }

    public Map<String, Token> getTokennames() {
        return tokennames;
    }

    public void setTokennames(Map<String, Token> tokennames) {
        this.tokennames = tokennames;
    }

    public void setOutputs(List<UTXO> outputs) {
        this.outputs = outputs;
    }
    
}
