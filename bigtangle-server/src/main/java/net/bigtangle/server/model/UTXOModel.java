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

    private byte[] coinvalue;

    private byte[] scriptbytes;
    private byte[] hash;
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

    private byte[] blockhash;
    private boolean confirmed;
    private boolean spent;
    private byte[] spenderblockhash;
    // create time of the block output
    private Long time;

    private Long scriptType;

    public static UTXOModel fromUTXO(UTXO out) {
        UTXOModel s = new UTXOModel();

        s.setHash(out.getTxHash().getBytes());
        // index is actually an unsigned int
        s.setOutputindex(out.getIndex());
        s.setCoinvalue(out.getValue().getValue().toByteArray());
        s.setScriptbytes(out.getScript().getProgram());
        s.setToaddress(out.getAddress());
        // s.setCoinbase( out.getScript().getScriptType().ordinal());
        s.setCoinbase(out.isCoinbase());
        s.setBlockhash(out.getBlockHash() != null ? out.getBlockHash().getBytes() : null);
        s.setTokenid(Utils.HEX.encode(out.getValue().getTokenid()));
        // if ((out.getFromaddress() == null ||
        // "".equals(out.getFromaddress())) && !out.isCoinbase()) {
        // log.debug(" no Fromaddress " + out.toString());
        // }
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
        s.setScript(new Script(getScriptbytes()));
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

    public byte[] getCoinvalue() {
        return coinvalue;
    }

    public void setCoinvalue(byte[] coinvalue) {
        this.coinvalue = coinvalue;
    }

    public byte[] getScriptbytes() {
        return scriptbytes;
    }

    public void setScriptbytes(byte[] scriptbytes) {
        this.scriptbytes = scriptbytes;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
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

    public byte[] getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(byte[] blockhash) {
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

    public byte[] getSpenderblockhash() {
        return spenderblockhash;
    }

    public void setSpenderblockhash(byte[] spenderblockhash) {
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
