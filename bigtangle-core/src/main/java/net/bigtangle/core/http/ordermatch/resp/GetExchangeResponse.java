/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.ordermatch.resp;

import java.util.List;

import net.bigtangle.core.Exchange;
import net.bigtangle.core.http.AbstractResponse;

public class GetExchangeResponse extends AbstractResponse {

    private List<Exchange> exchanges;
    
    public static AbstractResponse create(List<Exchange> exchanges) {
        GetExchangeResponse res = new GetExchangeResponse();
        res.exchanges = exchanges;
        return res;
    }

    public List<Exchange> getExchanges() {
        return exchanges;
    }
}
