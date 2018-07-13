/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service.response;

import net.bigtangle.core.OrderPublishList;

public class GetOrderResponse extends AbstractResponse {

    private OrderPublishList orders;
    
    public static AbstractResponse create( OrderPublishList orders) {
        GetOrderResponse res = new GetOrderResponse();
        res.orders = orders;
        return res;
    }

    public OrderPublishList getOrders() {
        return orders;
    }

    public void setOrders(OrderPublishList orders) {
        this.orders = orders;
    }
}
