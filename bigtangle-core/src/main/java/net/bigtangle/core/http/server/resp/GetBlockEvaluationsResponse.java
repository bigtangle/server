/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.http.AbstractResponse;

public class GetBlockEvaluationsResponse extends AbstractResponse {
    private List<BlockEvaluation> evaluations;

    public static AbstractResponse create(List<BlockEvaluation> evaluations) {
        GetBlockEvaluationsResponse res = new GetBlockEvaluationsResponse();
        res.evaluations = evaluations;
        return res;
    }

    public List<BlockEvaluation> getEvaluations() {
        return evaluations;
    }

}
