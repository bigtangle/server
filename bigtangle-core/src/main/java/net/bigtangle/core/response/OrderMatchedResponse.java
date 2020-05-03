/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

import java.util.List;

import net.bigtangle.core.OrderRecordMatched;

public class OrderMatchedResponse extends AbstractResponse {

    private List<OrderRecordMatched> orderRecordMatcheds;
 
    public static OrderMatchedResponse createOrderRecordResponse(List<OrderRecordMatched> tickers) {
        OrderMatchedResponse res = new OrderMatchedResponse();
        res.orderRecordMatcheds =  tickers;
        return res;
    }

    public List<OrderRecordMatched> getOrderRecordMatcheds() {
        return orderRecordMatcheds;
    }

    public void setOrderRecordMatcheds(List<OrderRecordMatched> orderRecordMatcheds) {
        this.orderRecordMatcheds = orderRecordMatcheds;
    }
     
}
