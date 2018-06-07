/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.response;

import java.util.List;

import net.bigtangle.core.PayMultiSign;

public class PayMultiSignListResponse extends AbstractResponse {

    private List<PayMultiSign> payMultiSigns;
    
    public static AbstractResponse create(List<PayMultiSign> payMultiSigns) {
        PayMultiSignListResponse res = new PayMultiSignListResponse();
        res.payMultiSigns = payMultiSigns;
        return res;
    }

    public List<PayMultiSign> getPayMultiSigns() {
        return payMultiSigns;
    }
}
