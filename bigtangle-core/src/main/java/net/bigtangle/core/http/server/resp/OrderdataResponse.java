/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.http.AbstractResponse;

public class OrderdataResponse extends AbstractResponse {

    private List<OrderRecord> allOrdersSorted;

    public static AbstractResponse createOrderRecordResponse(List<OrderRecord> allOrdersSorted) {
        OrderdataResponse res = new OrderdataResponse();
        res.allOrdersSorted = allOrdersSorted;
        return res;
    }

    public List<OrderRecord> getAllOrdersSorted() {
        return allOrdersSorted;
    }

    public void setAllOrdersSorted(List<OrderRecord> allOrdersSorted) {
        this.allOrdersSorted = allOrdersSorted;
    }

}
