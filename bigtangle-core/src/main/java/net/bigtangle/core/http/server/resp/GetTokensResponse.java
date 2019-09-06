/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.Token;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.http.AbstractResponse;

public class GetTokensResponse extends AbstractResponse {
  
    private List<Token> tokens;
    private List<TokenSerial> tokenSerials;
    private Map<String, BigInteger> amountMap;

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

    public static GetTokensResponse create(List<Token> tokens, Map<String, BigInteger> amountMap) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        res.amountMap = amountMap;
        return res;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public Map<String, BigInteger> getAmountMap() {
        return amountMap;
    }

  
    public List<TokenSerial> getTokenSerials() {
        return tokenSerials;
    }
}
