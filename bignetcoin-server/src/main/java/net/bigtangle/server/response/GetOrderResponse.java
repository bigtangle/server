/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.response;

import java.util.List;

import net.bigtangle.core.Order;

public class GetOrderResponse extends AbstractResponse {

    private List<Order> orders;
    
    public static AbstractResponse create(List<Order> orders) {
        GetOrderResponse res = new GetOrderResponse();
        res.orders = orders;
        return res;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }
}
