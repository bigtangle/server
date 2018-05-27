/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.response;

import java.util.List;

import net.bigtangle.core.Exchange;

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
