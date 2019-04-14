/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.Token;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.server.ordermatch.bean.MatchResults;

public class OrderTickerResponse extends AbstractResponse {

    private List<MatchResults> tickers;
    private Map<String, Token> tokennames;
    public static OrderTickerResponse createOrderRecordResponse(List<MatchResults> tickers,Map<String, Token> tokennames) {
        OrderTickerResponse res = new OrderTickerResponse();
        res.tickers =  tickers;
        res.tokennames =tokennames;
        return res;
    }
    public List<MatchResults> getTickers() {
        return tickers;
    }
    public void setTickers(List<MatchResults> tickers) {
        this.tickers = tickers;
    }
    public Map<String, Token> getTokennames() {
        return tokennames;
    }
    public void setTokennames(Map<String, Token> tokennames) {
        this.tokennames = tokennames;
    }

    
 

 
}
