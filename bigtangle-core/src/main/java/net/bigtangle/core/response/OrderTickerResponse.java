/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.Token;
import net.bigtangle.core.ordermatch.MatchResult;

public class OrderTickerResponse extends AbstractResponse {

    private List<MatchResult> tickers;
    private Map<String, Token> tokennames;
    public static OrderTickerResponse createOrderRecordResponse(List<MatchResult> tickers,Map<String, Token> tokennames) {
        OrderTickerResponse res = new OrderTickerResponse();
        res.tickers =  tickers;
        res.tokennames =tokennames;
        return res;
    }
    public List<MatchResult> getTickers() {
        return tickers;
    }
    public void setTickers(List<MatchResult> tickers) {
        this.tickers = tickers;
    }
    public Map<String, Token> getTokennames() {
        return tokennames;
    }
    public void setTokennames(Map<String, Token> tokennames) {
        this.tokennames = tokennames;
    }

    
 

 
}
