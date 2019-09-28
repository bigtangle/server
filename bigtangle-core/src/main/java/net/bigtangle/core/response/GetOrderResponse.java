/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.response;

import java.util.List;

import net.bigtangle.core.OTCOrder;

public class GetOrderResponse extends AbstractResponse {

    private List<OTCOrder> orders;
    
    public static AbstractResponse create(List<OTCOrder> orders) {
        GetOrderResponse res = new GetOrderResponse();
        res.orders = orders;
        return res;
    }

    public List<OTCOrder> getOrders() {
        return orders;
    }

    public void setOrders(List<OTCOrder> orders) {
        this.orders = orders;
    }
}
