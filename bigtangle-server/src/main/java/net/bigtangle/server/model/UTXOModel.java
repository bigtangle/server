package net.bigtangle.server.model;

import java.math.BigInteger;

import net.bigtangle.core.Coin;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.script.Script;

/*******************************************************************************
 * Copyright 2018 Inasset GmbH.
 * 
 *******************************************************************************/

public class UTXOModel {

    private String coinvalue;

    private String scriptbytes;
    private String hash;
    private Long outputindex;
    private boolean coinbase;
    private String toaddress;
    private long addresstargetable;
    private String fromaddress;

    private boolean spendpending;
    private long spendpendingtime;
    private String tokenid;

    private Long minimumsign;

    private String memo;

    private String blockhash;
    private boolean confirmed;
    private boolean spent;
    private String spenderblockhash;
    // create time of the block output
    private Long time;

    private Long scriptType;

    public static UTXOModel fromUTXO(UTXO out)   {
        UTXOModel s = new UTXOModel();

        s.setHash(Utils.HEX.encode(out.getTxHash().getBytes()));
        // index is actually an unsigned int
        s.setOutputindex(out.getIndex());
        s.setCoinvalue(Utils.HEX.encode(out.getValue().getValue().toByteArray()));
        s.setScriptbytes(Utils.HEX.encode(out.getScript().getProgram()));
        s.setToaddress(out.getAddress());
        // s.setCoinbase( out.getScript().getScriptType().ordinal());
        s.setCoinbase(out.isCoinbase());
        s.setBlockhash(Utils.HEX.encode(out.getBlockHash().getBytes() ));
        s.setTokenid(Utils.HEX.encode(out.getValue().getTokenid()));
 
        s.setFromaddress(out.getFromaddress());
        s.setMemo(out.getMemo());
        s.setSpent(out.isSpent());
        s.setConfirmed(out.isConfirmed());
        s.setSpendpending(out.isSpendPending());
        s.setTime(out.getTime());
        s.setSpendpendingtime(out.getSpendPendingTime());
        s.setMinimumsign(out.getMinimumsign());

        return s;
    }

    public UTXO toUTXO() {
        UTXO s = new UTXO();
        Coin coinvalue = new Coin(new BigInteger(getCoinvalue()), getTokenid());
        s.setHash(Sha256Hash.wrap(getHash()));
        // index is actually an unsigned int
        s.setIndex(getOutputindex());
        s.setValue(coinvalue);
        s.setScript(new Script(getScriptbytes().getBytes()));
        s.setAddress(getToaddress());
        // s.setCoinbase( getScript().getScriptType().ordinal());
        s.setCoinbase(isCoinbase());
        s.setBlockHash(Sha256Hash.wrap(getBlockhash()));
        s.setTokenId(getTokenid());
   
        s.setFromaddress(getFromaddress());
        s.setMemo(getMemo());
        s.setSpent(isSpent());
        s.setConfirmed(isConfirmed());
        s.setSpendPending(isSpendpending());
        s.setTime(getTime());
        s.setSpendPendingTime(getSpendpendingtime());
        s.setMinimumsign(getMinimumsign());

        return s;
    }

    public String getCoinvalue() {
        return coinvalue;
    }

    public void setCoinvalue(String coinvalue) {
        this.coinvalue = coinvalue;
    }

    public String getScriptbytes() {
        return scriptbytes;
    }

    public void setScriptbytes(String scriptbytes) {
        this.scriptbytes = scriptbytes;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getOutputindex() {
        return outputindex;
    }

    public void setOutputindex(Long outputindex) {
        this.outputindex = outputindex;
    }

    public boolean isCoinbase() {
        return coinbase;
    }

    public void setCoinbase(boolean coinbase) {
        this.coinbase = coinbase;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public long getAddresstargetable() {
        return addresstargetable;
    }

    public void setAddresstargetable(long addresstargetable) {
        this.addresstargetable = addresstargetable;
    }

    public String getFromaddress() {
        return fromaddress;
    }

    public void setFromaddress(String fromaddress) {
        this.fromaddress = fromaddress;
    }

    public boolean isSpendpending() {
        return spendpending;
    }

    public void setSpendpending(boolean spendpending) {
        this.spendpending = spendpending;
    }

    public long getSpendpendingtime() {
        return spendpendingtime;
    }

    public void setSpendpendingtime(long spendpendingtime) {
        this.spendpendingtime = spendpendingtime;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public Long getMinimumsign() {
        return minimumsign;
    }

    public void setMinimumsign(Long minimumsign) {
        this.minimumsign = minimumsign;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isSpent() {
        return spent;
    }

    public void setSpent(boolean spent) {
        this.spent = spent;
    }

    public String getSpenderblockhash() {
        return spenderblockhash;
    }

    public void setSpenderblockhash(String spenderblockhash) {
        this.spenderblockhash = spenderblockhash;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getScriptType() {
        return scriptType;
    }

    public void setScriptType(Long scriptType) {
        this.scriptType = scriptType;
    }

}
