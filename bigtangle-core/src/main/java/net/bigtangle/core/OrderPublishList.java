/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.util.List;

public class OrderPublishList implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    List<OrderPublish> orderPublishList;

    public List<OrderPublish> getOrderPublishList() {
        return orderPublishList;
    }

    public void setOrderPublishList(List<OrderPublish> orderPublishList) {
        this.orderPublishList = orderPublishList;
    }
    
}
