/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.beans.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;

import org.bitcoinj.utils.MonetaryFormat;

import com.google.common.math.LongMath;
import com.google.common.primitives.Longs;

/**
 * Represents a monetary Coin value. This class is immutable.
 * The coin is set to minimal MILLICOIN for micro payment of machine.
 */
public final class Coin implements Monetary, Comparable<Coin>, Serializable {

    private static final long serialVersionUID = 551802452657362699L;

    /**
     * Number of decimals for one Coin. This constant is useful for quick
     * adapting to other coins because a lot of constants derive from it.
     */
    public static final int SMALLEST_UNIT_EXPONENT = 8;

    /**
     * The number of satoshis equal to one bitcoin.
     */
    public static final long COIN_VALUE = LongMath.pow(10, SMALLEST_UNIT_EXPONENT);

    /**
     * Zero Bitcoins.
     */
    public static final Coin ZERO = Coin.valueOf(0, NetworkParameters.BIGNETCOIN_TOKENID);

    /**
     * One Bitcoin.
     */
    public static final Coin COIN = Coin.valueOf(COIN_VALUE, NetworkParameters.BIGNETCOIN_TOKENID);

    /**
     * 0.01 Bitcoins. This unit is not really used much.
     */
    public static final Coin CENT = COIN.divide(100);

    /**
     * 0.001 Bitcoins, also known as 1 mBTC.
     */
    public static final Coin MILLICOIN = COIN.divide(1000);

     
    /**
     * A satoshi is the smallest unit that can be transferred. 100 million of
     * them fit into a Bitcoin.
     */
    public static final Coin SATOSHI = Coin.valueOf(1, NetworkParameters.BIGNETCOIN_TOKENID);

    public static final Coin FIFTY_COINS = COIN.multiply(50);

    /**
     * Represents a monetary value of minus one satoshi.
     */
    public static final Coin NEGATIVE_SATOSHI = Coin.valueOf(-1, NetworkParameters.BIGNETCOIN_TOKENID);

    /**
     * The number of satoshis of this monetary value.
     */
    public final long value;
    public final byte[] tokenid;

    private Coin(final long satoshis, final byte[] tokenid) {
        this.value = satoshis;
        this.tokenid = tokenid;
    }

    public static Coin valueOf(final long satoshis, byte[] tokenid) {
        return new Coin(satoshis, tokenid);
    }

    @Override
    public int smallestUnitExponent() {
        return SMALLEST_UNIT_EXPONENT;
    }

    /**
     * Returns the number of satoshis of this monetary value.
     */
    @Override
    public long getValue() {
        return value;
    }
    
    public String getTokenHex() {
        if (tokenid == null) {
            return "";
        }
        String hexStr = Utils.HEX.encode(this.tokenid);
        return hexStr;
    }

    /**
     * Parses an amount expressed in the way humans are used to.
     * <p>
     * <p/>
     * This takes string in a format understood by
     * {@link BigDecimal#BigDecimal(String)}, for example "0", "1", "0.10",
     * "1.23E3", "1234.5E-5".
     *
     * @throws IllegalArgumentException
     *             if you try to specify fractional satoshis, or a value out of
     *             range.
     */
    public static Coin parseCoin(final String str, byte[] tokenid) {
        try {
            long satoshis = new BigDecimal(str).movePointRight(SMALLEST_UNIT_EXPONENT).toBigIntegerExact().longValue();
            return Coin.valueOf(satoshis, tokenid);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e); // Repackage exception to
                                                   // honor method contract
        }
    }

    public Coin add(final Coin value) {
        if (! Arrays.equals( this.tokenid, value.tokenid)) {
            throw new IllegalArgumentException("!this.tokenid.equals( value.tokenid)");
        }
        return new Coin(LongMath.checkedAdd(this.value, value.value), value.tokenid);
    }

    /** Alias for add */
    public Coin plus(final Coin value) {
        return add(value);
    }

    public Coin subtract(final Coin value) {
        if (! Arrays.equals( this.tokenid, value.tokenid)) {
            throw new IllegalArgumentException("");
        }
        return new Coin(LongMath.checkedSubtract(this.value, value.value), value.tokenid);
    }

    /** Alias for subtract */
    public Coin minus(final Coin value) {
        return subtract(value);
    }

    public Coin multiply(final long factor) {
        return new Coin(LongMath.checkedMultiply(this.value, factor), this.tokenid);
    }

    /** Alias for multiply */
    public Coin times(final long factor) {
        return multiply(factor);
    }

    /** Alias for multiply */
    public Coin times(final int factor) {
        return multiply(factor);
    }

    public Coin divide(final long divisor) {
        return new Coin(this.value / divisor, this.tokenid);
    }

    /** Alias for divide */
    public Coin div(final long divisor) {
        return divide(divisor);
    }

    /** Alias for divide */
    public Coin div(final int divisor) {
        return divide(divisor);
    }

    public Coin[] divideAndRemainder(final long divisor) {
        return new Coin[] { new Coin(this.value / divisor, this.tokenid),
                new Coin(this.value % divisor, this.tokenid) };
    }

    public long divide(final Coin divisor) {
        return this.value / divisor.value;
    }

    /**
     * Returns true if and only if this instance represents a monetary value
     * greater than zero, otherwise false.
     */
    @Transient
    public boolean isPositive() {
        return signum() == 1;
    }

    /**
     * Returns true if and only if this instance represents a monetary value
     * less than zero, otherwise false.
     */
    @Transient
    public boolean isNegative() {
        return signum() == -1;
    }

    /**
     * Returns true if and only if this instance represents zero monetary value,
     * otherwise false.
     */
    @Transient
    public boolean isZero() {
        return signum() == 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is
     * greater than that of the given other Coin, otherwise false.
     */
    public boolean isGreaterThan(Coin other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is less
     * than that of the given other Coin, otherwise false.
     */
    public boolean isLessThan(Coin other) {
        return compareTo(other) < 0;
    }

    public Coin shiftLeft(final int n) {
        return new Coin(this.value << n, this.tokenid);
    }

    public Coin shiftRight(final int n) {
        return new Coin(this.value >> n, this.tokenid);
    }

    @Override
    public int signum() {
        if (this.value == 0)
            return 0;
        return this.value < 0 ? -1 : 1;
    }

    public Coin negate() {
        return new Coin(-this.value, this.tokenid);
    }

    /**
     * Returns the number of satoshis of this monetary value. It's deprecated in
     * favour of accessing {@link #value} directly.
     */
    public long longValue() {
        return this.value;
    }

    private static final MonetaryFormat FRIENDLY_FORMAT = MonetaryFormat.FIAT.minDecimals(2).repeatOptionalDecimals(1, 6)
            .postfixCode();

    /**
     * Returns the value as a 0.12 type string. More digits after the decimal
     * place will be used if necessary, but two will always be present.
     */
    public String toFriendlyString() {
        if(Arrays.equals(  NetworkParameters.BIGNETCOIN_TOKENID, tokenid)) {
        return FRIENDLY_FORMAT.format(this).toString();}
        else {
          return   toString();
        }
    }

    private static final MonetaryFormat PLAIN_FORMAT = MonetaryFormat.FIAT.minDecimals(0).repeatOptionalDecimals(1, 8)
            .noCode();

    /**
     * <p>
     * Returns the value as a plain string denominated in BTA. The result is
     * unformatted with no trailing zeroes. For instance, a value of 150000
     * satoshis gives an output string of "0.0015" BTA
     * </p>
     */
    public String toPlainString() {
        return PLAIN_FORMAT.format(this).toString();
    }

    @Override
    public String toString() {
        return "[" + value + ", " + getTokenHex() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Coin other = (Coin) obj;
        if (tokenid != other.tokenid)
            return false;
        if (value != other.value)
            return false;
        return true;
    }

    @Override
    public int compareTo(final Coin other) {
        return Longs.compare(this.value, other.value);
    }

    public byte[] getTokenid() {
        return tokenid;
    }
    
}
