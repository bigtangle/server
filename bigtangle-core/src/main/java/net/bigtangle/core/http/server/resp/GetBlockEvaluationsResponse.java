/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.http.AbstractResponse;

public class GetBlockEvaluationsResponse extends AbstractResponse {
    private List<BlockEvaluationDisplay> evaluations;

    public static AbstractResponse create(List<BlockEvaluationDisplay> evaluations) {
        GetBlockEvaluationsResponse res = new GetBlockEvaluationsResponse();
        res.evaluations = evaluations;
        return res;
    }

    public List<BlockEvaluationDisplay> getEvaluations() {
        return evaluations;
    }

}
