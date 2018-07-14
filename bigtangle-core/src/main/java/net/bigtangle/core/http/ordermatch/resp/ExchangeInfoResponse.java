/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.ordermatch.resp;

import net.bigtangle.core.Exchange;
import net.bigtangle.core.http.AbstractResponse;

public class ExchangeInfoResponse extends AbstractResponse {

    private Exchange exchange;
    
    public static AbstractResponse create(Exchange exchange) {
        ExchangeInfoResponse res = new ExchangeInfoResponse();
        res.exchange = exchange;
        return res;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }
}
