/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2014 Andreas Schildbach
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
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;

import com.google.common.math.LongMath;
import com.google.common.primitives.Longs;

import net.bigtangle.utils.MonetaryFormat;

/**
 * Represents a monetary Coin value. This class is immutable. Only the BIG has 2
 * digit decimal, all others coins are long number.
 */
public final class Coin implements Monetary, Comparable<Coin>, Serializable {

    public Coin() {
    }

    private static final long serialVersionUID = 551802452657362699L;

    /**
     * Number of decimals for one Coin. This constant is useful for quick
     * adapting to other coins because a lot of constants derive from it.
     */
    public static final int SMALLEST_UNIT_EXPONENT = 2;

    /**
     * The number of satoshis equal to one bitcoin.
     */
    public static final long COIN_VALUE = LongMath.pow(10, SMALLEST_UNIT_EXPONENT);

    /**
     * Zero Bitcoins.
     */
    public static final Coin ZERO = Coin.valueOf(0, NetworkParameters.BIGTANGLE_TOKENID);

    /**
     * One Bitcoin.
     */
    public static final Coin COIN = Coin.valueOf(COIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID);

    /**
     * 0.01 Bitcoins. This unit is not really used much.
     */
    public static final Coin CENT = COIN.divide(100);
    /**
     * A satoshi is the smallest unit that can be transferred.
     */
    public static final Coin SATOSHI = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);

    public static final Coin FIFTY_COINS = COIN.multiply(50);

    /**
     * Represents a monetary value of minus one satoshi.
     */
    public static final Coin NEGATIVE_SATOSHI = Coin.valueOf(-1, NetworkParameters.BIGTANGLE_TOKENID);

    /**
     * The number of satoshis of this monetary value.
     */
    private long value;
    private byte[] tokenid;

    private Coin(final long satoshis, final byte[] tokenid) {
        this.value = satoshis;
        this.tokenid = tokenid;
    }

    public static Coin valueOf(final long satoshis, byte[] tokenid) {
        return new Coin(satoshis, tokenid);
    }

    public static Coin valueOf(final long satoshis, String tokenid) {
        byte[] buf = Utils.HEX.decode(tokenid);
        return new Coin(satoshis, buf);
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

    public void setValue(long value) {
        this.value = value;
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
     *
     * @throws IllegalArgumentException
     *             if you try to specify fractional, or a value out of range.
     */
    public static Coin parseCoin(final String str, byte[] tokenid) {
        try {
            if (Arrays.equals(tokenid, NetworkParameters.BIGTANGLE_TOKENID)) {
                long satoshis = new BigDecimal(str).movePointRight(SMALLEST_UNIT_EXPONENT).toBigIntegerExact()
                        .longValue();
                return Coin.valueOf(satoshis, tokenid);
            } else {
                return Coin.valueOf(Long.valueOf(str), tokenid);
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e); // Repackage exception to
                                                   // honor method contract
        }
    }

    public Coin add(final Coin value) {
        if (!Arrays.equals(this.tokenid, value.tokenid)) {
            throw new IllegalArgumentException("!this.tokenid.equals( value.tokenid)");
        }
        return new Coin(LongMath.checkedAdd(this.value, value.value), value.tokenid);
    }

    /** Alias for add */
    public Coin plus(final Coin value) {
        return add(value);
    }

    public Coin subtract(final Coin value) {
        if (!Arrays.equals(this.tokenid, value.tokenid)) {
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

    public boolean isBIG() {
        return Arrays.equals(this.tokenid, NetworkParameters.BIGTANGLE_TOKENID);

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

    private static final MonetaryFormat PLAIN_FORMAT = MonetaryFormat.FIAT.minDecimals(0).repeatOptionalDecimals(1, 2)
            .noCode();

    /**
     * <p>
     * Returns the value as a plain string. The result is unformatted with no
     * trailing zeroes.
     * </p>
     */
    public String toPlainString() {
        if (isBIG()) {
            return PLAIN_FORMAT.format(this).toString();
        } else {
            return String.valueOf(this.value);
        }
    }

    public static String toPlainString(long value) {

        return PLAIN_FORMAT.format(Coin.valueOf(value, NetworkParameters.BIGTANGLE_TOKENID)).toString();

    }

    @Override
    public String toString() {
        return "[" + toPlainString() + " #" + getTokenHex() + "]";
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
