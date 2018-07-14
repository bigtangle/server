/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.http.AbstractResponse;

public class PayMultiSignAddressListResponse extends AbstractResponse {

    private List<PayMultiSignAddress> payMultiSignAddresses;

    public static AbstractResponse create(List<PayMultiSignAddress> payMultiSignAddresses) {
        PayMultiSignAddressListResponse res = new PayMultiSignAddressListResponse();
        res.payMultiSignAddresses = payMultiSignAddresses;
        return res;
    }

    public List<PayMultiSignAddress> getPayMultiSignAddresses() {
        return payMultiSignAddresses;
    }

    public void setPayMultiSignAddresses(List<PayMultiSignAddress> payMultiSignAddresses) {
        this.payMultiSignAddresses = payMultiSignAddresses;
    }
}
