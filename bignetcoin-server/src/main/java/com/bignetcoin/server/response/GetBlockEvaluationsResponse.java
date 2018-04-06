package com.bignetcoin.server.response;

import java.util.List;

import net.bigtangle.core.BlockEvaluation;

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
