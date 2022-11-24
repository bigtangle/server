/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import java.math.BigInteger;

import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.Utils;

public class PayMultiSignModel implements java.io.Serializable {

    private static final long serialVersionUID = 8438153762231442643L;

    private String orderid;

    private String tokenid;

    private String toaddress;

    private String blockhashHex;
    private String tokenBlockhashHex;

    private String blockhash;

    private String amount;

    private long minsignnumber;

    private String pubKeyhex;

    private String outputhashhex;
    private long outputindex;
    private int sign;
    private int signcount;

    public static PayMultiSignModel from(PayMultiSign payMultiSign) {
        PayMultiSignModel p = new PayMultiSignModel();
        p.setOrderid(payMultiSign.getOrderid());
        p.setTokenid(payMultiSign.getTokenid());
        p.setToaddress(payMultiSign.getToaddress());
        p.setBlockhash(Utils.HEX.encode(payMultiSign.getBlockhash()));
        p.setAmount(Utils.HEX.encode(payMultiSign.getAmount().toByteArray()));
        p.setMinsignnumber(payMultiSign.getMinsignnumber());
        p.setOutputhashhex(payMultiSign.getOutputHashHex());
        p.setOutputindex(payMultiSign.getOutputindex());
        p.setSigncount(payMultiSign.getSigncount());
        return p;
    }

    public PayMultiSign toPayMultiSign() {
        PayMultiSign p = new PayMultiSign();
        p.setOrderid(getOrderid());
        p.setTokenid(getTokenid());
        p.setToaddress(getToaddress());
        p.setBlockhash(Utils.HEX.decode(getBlockhash()));
        p.setAmount(new BigInteger(Utils.HEX.decode(getAmount())));
        p.setMinsignnumber(getMinsignnumber());
        p.setOutputHashHex(getOutputhashhex());
        p.setOutputindex(getOutputindex());
        p.setSigncount(getSigncount());
        return p;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public String getBlockhashHex() {
        return blockhashHex;
    }

    public void setBlockhashHex(String blockhashHex) {
        this.blockhashHex = blockhashHex;
    }

    public String getTokenBlockhashHex() {
        return tokenBlockhashHex;
    }

    public void setTokenBlockhashHex(String tokenBlockhashHex) {
        this.tokenBlockhashHex = tokenBlockhashHex;
    }

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public long getMinsignnumber() {
        return minsignnumber;
    }

    public void setMinsignnumber(long minsignnumber) {
        this.minsignnumber = minsignnumber;
    }

    public String getPubKeyhex() {
        return pubKeyhex;
    }

    public void setPubKeyhex(String pubKeyhex) {
        this.pubKeyhex = pubKeyhex;
    }

    public String getOutputhashhex() {
        return outputhashhex;
    }

    public void setOutputhashhex(String outputhashhex) {
        this.outputhashhex = outputhashhex;
    }

    public long getOutputindex() {
        return outputindex;
    }

    public void setOutputindex(long outputindex) {
        this.outputindex = outputindex;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public int getSigncount() {
        return signcount;
    }

    public void setSigncount(int signcount) {
        this.signcount = signcount;
    }

}
