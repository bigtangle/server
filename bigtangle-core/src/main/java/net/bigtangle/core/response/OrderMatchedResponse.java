/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

import java.util.List;

import net.bigtangle.core.Lockobject;

public class OrderMatchedResponse extends AbstractResponse {

    private List<Lockobject> lockobjects;
 
    public static OrderMatchedResponse createOrderRecordResponse(List<Lockobject> tickers) {
        OrderMatchedResponse res = new OrderMatchedResponse();
        res.lockobjects =  tickers;
        return res;
    }

    public List<Lockobject> getOrderRecordMatcheds() {
        return lockobjects;
    }

    public void setOrderRecordMatcheds(List<Lockobject> lockobjects) {
        this.lockobjects = lockobjects;
    }
     
}
