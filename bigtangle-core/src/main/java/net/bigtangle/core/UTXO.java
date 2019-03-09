/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2012 Matt Corallo.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bigtangle.core;

import java.beans.Transient;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Objects;

import net.bigtangle.script.Script;

/**
 * A UTXO message contains the information necessary to check a spending
 * transaction and consensus logic. It does not stand for Unspent Transaction Output
 * but for Used Transaction Output, i.e. they are not necessarily unspent.
 */
public class UTXO {

    public UTXO() {
    }

    public void setScriptHex(String scriptHex) {
        this.script = new Script(Utils.HEX.decode(scriptHex));
    }

    public void setHashHex(String hashHex) {
        this.hash = Sha256Hash.wrap(hashHex);
    }

    public void setBlockHashHex(String blockHashHex) {
        this.blockhash = Sha256Hash.wrap(blockHashHex);
    }

    public void setValue(Coin value) {
        this.value = value;
    }

    public void setHash(Sha256Hash hash) {
        this.hash = hash;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public void setCoinbase(boolean coinbase) {
        this.coinbase = coinbase;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    private Coin value;
    @JsonIgnore
    private Script script;
    private Sha256Hash hash;
    private long index;
    private boolean coinbase;
    private String address;
    private Sha256Hash blockhash;
    private String fromaddress;
    private String memo;
    private boolean spent;
    private boolean confirmed;
    private boolean spendPending;
    private String tokenId;
    private long time;

    private long minimumsign;

    public boolean isMultiSig() {
        return minimumsign > 1l;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenid) {
        this.tokenId = tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenId = tokenid;
    }

    @Transient
    public byte[] getTokenidBuf() {
        return Utils.HEX.decode(this.tokenId);
    }

    /**
     * Creates a stored transaction output.
     *
     * @param hash
     *            The hash of the containing transaction.
     * @param index
     *            The outpoint.
     * @param value
     *            The value available.
     * @param height
     *            The height this output was created in.
     * @param coinbase
     *            The coinbase flag.
     * @param address
     *            The address.
     */
    public UTXO(Sha256Hash hash, long index, Coin value, boolean coinbase, Script script, String address,
            Sha256Hash blockhash, String fromaddress, String memo, String tokenid, boolean spent, boolean confirmed,
            boolean spendPending, long minimumsign) {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.script = script;
        this.coinbase = coinbase;
        this.blockhash = blockhash;
        this.fromaddress = fromaddress;
        this.memo = memo;
        this.address = address;
        this.spent = spent;
        this.tokenId = tokenid;
        this.confirmed = confirmed;
        this.spendPending = spendPending;
        this.minimumsign = minimumsign;
    }

    public UTXO(InputStream in) throws IOException {
        byte[] valueBytes = new byte[8];
        if (in.read(valueBytes, 0, 8) != 8)
            throw new EOFException();

        byte[] tokenid = new byte[20];
        if (in.read(tokenid) != 20)
            throw new EOFException();
        value = Coin.valueOf(Utils.readInt64(valueBytes, 0), tokenid);

        int scriptBytesLength = ((in.read() & 0xFF)) | ((in.read() & 0xFF) << 8) | ((in.read() & 0xFF) << 16)
                | ((in.read() & 0xFF) << 24);
        byte[] scriptBytes = new byte[scriptBytesLength];
        if (in.read(scriptBytes) != scriptBytesLength)
            throw new EOFException();
        script = new Script(scriptBytes);

        byte[] hashBytes = new byte[32];
        if (in.read(hashBytes) != 32)
            throw new EOFException();
        hash = Sha256Hash.wrap(hashBytes);

        byte[] indexBytes = new byte[4];
        if (in.read(indexBytes) != 4)
            throw new EOFException();
        index = Utils.readUint32(indexBytes, 0);

//        height = ((in.read() & 0xFF)) | ((in.read() & 0xFF) << 8) | ((in.read() & 0xFF) << 16)
//                | ((in.read() & 0xFF) << 24);

        byte[] coinbaseByte = new byte[1];
        in.read(coinbaseByte);
        coinbase = coinbaseByte[0] == 1;
    }

    /** The value which this Transaction output holds. */
    public Coin getValue() {
        return value;
    }

    /**
     * The Script object which you can use to get address, script bytes or
     * script type.
     */
    @Transient
    public Script getScript() {
        return script;
    }

    public void setScript(Script script) {
        this.script = script;
    }

    public String getScriptHex() {
        return Utils.HEX.encode(this.script.getProgram());
    }

    /** The hash of the transaction which holds this output. */
    @Transient
    public Sha256Hash getHash() {
        return hash;
    }

    public String getHashHex() {
        return Utils.HEX.encode(hash.getBytes());
    }

    /** The index of this output in the transaction which holds it. */
    public long getIndex() {
        return index;
    }

    /** Gets the flag of whether this was created by a coinbase tx. */
    public boolean isCoinbase() {
        return coinbase;
    }

    /**
     * The address of this output, can be the empty string if none was provided
     * at construction time or was deserialized
     */
    public String getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Stored TxOut of %s (%s:%d)", value.toString(), hash, index);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getIndex(), getHash());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UTXO other = (UTXO) o;
        return getIndex() == other.getIndex() && getHash().equals(other.getHash());
    }

    public void serializeToStream(OutputStream bos) throws IOException {
        Utils.uint64ToByteStreamLE(BigInteger.valueOf(value.getValue()), bos);

        byte[] scriptBytes = script.getProgram();
        bos.write(0xFF & scriptBytes.length);
        bos.write(0xFF & scriptBytes.length >> 8);
        bos.write(0xFF & (scriptBytes.length >> 16));
        bos.write(0xFF & (scriptBytes.length >> 24));
        bos.write(scriptBytes);

        bos.write(hash.getBytes());
        Utils.uint32ToByteStreamLE(index, bos);

//        bos.write((int) (0xFF & (height)));
//        bos.write((int) (0xFF & (height >> 8)));
//        bos.write((int) (0xFF & (height >> 16)));
//        bos.write((int) (0xFF & (height >> 24)));

        bos.write(new byte[] { (byte) (coinbase ? 1 : 0) });
    }

    @Transient
    public Sha256Hash getBlockhash() {
        return blockhash;
    }

    public String getBlockHashHex() {
        return this.blockhash != null ? Utils.HEX.encode(this.blockhash.getBytes()) : "";
    }

    public void setBlockhash(Sha256Hash blockhash) {
        this.blockhash = blockhash;
    }

    public String getFromaddress() {
        return fromaddress;
    }

    public void setFromaddress(String fromaddress) {
        this.fromaddress = fromaddress;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public boolean isSpent() {
        return spent;
    }

    public void setSpent(boolean spent) {
        this.spent = spent;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isSpendPending() {
        return spendPending;
    }

    public void setSpendPending(boolean spendPending) {
        this.spendPending = spendPending;
    }

    public long getMinimumsign() {
        return minimumsign;
    }

    public void setMinimumsign(long minimumsign) {
        this.minimumsign = minimumsign;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

}
