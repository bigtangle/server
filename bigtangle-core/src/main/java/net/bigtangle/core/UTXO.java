/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import com.google.common.base.Objects;

import net.bigtangle.script.*;

import java.beans.Transient;
import java.io.*;
import java.math.*;
import java.util.Locale;

// TODO: Fix this class: should not talk about addresses, height should be optional/support mempool height etc

/**
 * A UTXO message contains the information necessary to check a spending
 * transaction. It avoids having to store the entire parentTransaction just to
 * get the hash and index. Useful when working with free standing outputs.
 */
public class UTXO {

    private Coin value;
    private Script script;
    private Sha256Hash hash;
    private long index;
    private long height;
    private boolean coinbase;
    private String address;
    private Sha256Hash blockhash;
    private String fromaddress;
    private String description;
    private boolean spent;
    private boolean confirmed;
    private boolean spendPending;
    private String tokenid;

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }
    
    @Transient 
    public byte[] getTokenidBuf() {
        return Utils.HEX.decode(this.tokenid);
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
    public UTXO(Sha256Hash hash, long index, Coin value, long height, boolean coinbase, Script script, String address,
            Sha256Hash blockhash, String fromaddress, String description, String tokenid, boolean spent, boolean confirmed, boolean spendPending) {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.height = height;
        this.script = script;
        this.coinbase = coinbase;
        this.blockhash = blockhash;
        this.fromaddress = fromaddress;
        this.description = description;
        this.address = address;
        this.spent = spent;
        this.tokenid = tokenid;
        this.confirmed = confirmed;
        this.spendPending = spendPending;
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

        height = ((in.read() & 0xFF)) | ((in.read() & 0xFF) << 8) | ((in.read() & 0xFF) << 16)
                | ((in.read() & 0xFF) << 24);

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

    /** Gets the height of the block that created this output. */
    public long getHeight() {
        return height;
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
        return String.format(Locale.US, "Stored TxOut of %s (%s:%d)", value.toFriendlyString(), hash, index);
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
        Utils.uint64ToByteStreamLE(BigInteger.valueOf(value.value), bos);

        byte[] scriptBytes = script.getProgram();
        bos.write(0xFF & scriptBytes.length);
        bos.write(0xFF & scriptBytes.length >> 8);
        bos.write(0xFF & (scriptBytes.length >> 16));
        bos.write(0xFF & (scriptBytes.length >> 24));
        bos.write(scriptBytes);

        bos.write(hash.getBytes());
        Utils.uint32ToByteStreamLE(index, bos);

        bos.write((int) (0xFF & (height)));
        bos.write((int) (0xFF & (height >> 8)));
        bos.write((int) (0xFF & (height >> 16)));
        bos.write((int) (0xFF & (height >> 24)));

        bos.write(new byte[] { (byte) (coinbase ? 1 : 0) });
    }

    @Transient
    public Sha256Hash getBlockhash() {
        return blockhash;
    }

    public String getBlockHashHex() {
        return Utils.HEX.encode(this.blockhash.getBytes());
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

}
