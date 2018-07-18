/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.ordermatch.resp;

import java.util.List;

import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.http.AbstractResponse;

public class GetOrderResponse extends AbstractResponse {

    private List<OrderPublish> orders;
    
    public static AbstractResponse create(List<OrderPublish> orders) {
        GetOrderResponse res = new GetOrderResponse();
        res.orders = orders;
        return res;
    }

    public List<OrderPublish> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderPublish> orders) {
        this.orders = orders;
    }
}