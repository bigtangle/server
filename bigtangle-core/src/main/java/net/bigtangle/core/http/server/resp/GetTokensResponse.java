/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.Token;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.http.AbstractResponse;

public class GetTokensResponse extends AbstractResponse {
  
    private List<Token> tokens;
    private List<TokenSerial> tokenSerials;
    private Map<String, Long> amountMap;

    public static GetTokensResponse createTokenSerial(List<TokenSerial> tokenSerials) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokenSerials = tokenSerials;
        return res;
    }
 

    public static GetTokensResponse create(List<Token> tokens) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        return res;
    }

    public static GetTokensResponse create(List<Token> tokens, Map<String, Long> amountMap) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        res.amountMap = amountMap;
        return res;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public Map<String, Long> getAmountMap() {
        return amountMap;
    }

  
    public List<TokenSerial> getTokenSerials() {
        return tokenSerials;
    }
}
