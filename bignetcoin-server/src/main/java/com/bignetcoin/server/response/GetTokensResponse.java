/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.response;

import java.util.List;

import net.bigtangle.core.Tokens;

public class GetTokensResponse extends AbstractResponse {

    private List<Tokens> tokens;
    
    public static AbstractResponse create(List<Tokens> tokens) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        return res;
    }

    public List<Tokens> getTokens() {
        return tokens;
    }
}
