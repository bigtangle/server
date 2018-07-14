/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.http.AbstractResponse;

public class PayMultiSignDetailsResponse extends AbstractResponse {

    private PayMultiSign payMultiSign;

    public static AbstractResponse create(PayMultiSign payMultiSign) {
        PayMultiSignDetailsResponse res = new PayMultiSignDetailsResponse();
        res.payMultiSign = payMultiSign;
        return res;
    }

    public PayMultiSign getPayMultiSign() {
        return payMultiSign;
    }

    public void setPayMultiSign(PayMultiSign payMultiSign) {
        this.payMultiSign = payMultiSign;
    }
}
