/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service.response;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;

public class GetTokensResponse extends AbstractResponse {
    private Tokens token;
    private List<Tokens> tokens;
    private List<TokenSerial> tokenSerials;
    private Map<String, Long> amountMap;

    public static AbstractResponse createTokenSerial(List<TokenSerial> tokenSerials) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokenSerials = tokenSerials;
        return res;
    }

    public static AbstractResponse create(Tokens token) {
        GetTokensResponse res = new GetTokensResponse();
        res.token = token;
        return res;
    }

    public static AbstractResponse create(List<Tokens> tokens) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        return res;
    }

    public static AbstractResponse create(List<Tokens> tokens, Map<String, Long> amountMap) {
        GetTokensResponse res = new GetTokensResponse();
        res.tokens = tokens;
        res.amountMap = amountMap;
        return res;
    }

    public List<Tokens> getTokens() {
        return tokens;
    }

    public Map<String, Long> getAmountMap() {
        return amountMap;
    }

    public Tokens getToken() {
        return token;
    }

    public List<TokenSerial> getTokenSerials() {
        return tokenSerials;
    }
}
