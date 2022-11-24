/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Utils;

public class PayMultiSignAddressModel implements java.io.Serializable {

    private static final long serialVersionUID = 2618349280766774228L;

    private String orderid;

    private String pubkey;

    private int sign;

    private String signInputdata;

    private int signindex;

    public static PayMultiSignAddressModel from(PayMultiSignAddress payMultiSignAddress) {
        PayMultiSignAddressModel p = new PayMultiSignAddressModel();
        p.setOrderid(payMultiSignAddress.getOrderid());
        p.setPubkey(payMultiSignAddress.getPubKey());
        p.setSign(payMultiSignAddress.getSign());
        p.setSignInputdata(Utils.HEX.encode(payMultiSignAddress.getSignInputData()));
        p.setSignindex(payMultiSignAddress.getSignIndex());
        return p;
    }

    public PayMultiSignAddress toPayMultiSignAddress() {
        PayMultiSignAddress p = new PayMultiSignAddress();
        p.setOrderid(getOrderid());
        p.setPubKey(getPubkey());
        p.setSign(getSign());
        p.setSignInputData(Utils.HEX.decode(getSignInputdata()));
        p.setSignIndex(getSignindex());
        return p;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public String getSignInputdata() {
        return signInputdata;
    }

    public void setSignInputdata(String signInputdata) {
        this.signInputdata = signInputdata;
    }

    public int getSignindex() {
        return signindex;
    }

    public void setSignindex(int signindex) {
        this.signindex = signindex;
    }

}
