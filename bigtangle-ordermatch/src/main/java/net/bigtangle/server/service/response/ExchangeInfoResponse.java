/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.response;

import net.bigtangle.core.Exchange;

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
