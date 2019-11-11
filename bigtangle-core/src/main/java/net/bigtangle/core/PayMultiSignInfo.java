/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.ArrayList;
import java.util.List;

public class PayMultiSignInfo {

    private PayMultiSign payMultiSign;
    
    private List<PayMultiSignAddress> payMultiSignAddresses = new ArrayList<PayMultiSignAddress>();

    public PayMultiSign getPayMultiSign() {
        return payMultiSign;
    }

    public void setPayMultiSign(PayMultiSign payMultiSign) {
        this.payMultiSign = payMultiSign;
    }

    public List<PayMultiSignAddress> getPayMultiSignAddresses() {
        return payMultiSignAddresses;
    }

    public void setPayMultiSignAddresses(List<PayMultiSignAddress> payMultiSignAddresses) {
        this.payMultiSignAddresses = payMultiSignAddresses;
    }
}
