/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.utils;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.math.BigInteger;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;

import com.google.common.base.Objects;

/**
 * An exchange rate is expressed as a ratio of a {@link Coin} and a {@link Fiat} amount.
 */
public class ExchangeRate implements Serializable {

    public final Coin coin;
    public final Fiat fiat;

    /** Construct exchange rate. This amount of coin is worth that amount of fiat. */
    public ExchangeRate(Coin coin, Fiat fiat) {
        checkArgument(coin.isPositive());
        checkArgument(fiat.isPositive());
        checkArgument(fiat.currencyCode != null, "currency code required");
        this.coin = coin;
        this.fiat = fiat;
    }

    /** Construct exchange rate. One coin is worth this amount of fiat. */
    public ExchangeRate(Fiat fiat) {
        this(Coin.COIN, fiat);
    }

    /**
     * Convert a coin amount to a fiat amount using this exchange rate.
     * @throws ArithmeticException if the converted fiat amount is too high or too low.
     */
    public Fiat coinToFiat(Coin convertCoin) {
        // Use BigInteger because it's much easier to maintain full precision without overflowing.
        final BigInteger converted = BigInteger.valueOf(convertCoin.value).multiply(BigInteger.valueOf(fiat.value))
                .divide(BigInteger.valueOf(coin.value));
        if (converted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        return Fiat.valueOf(fiat.currencyCode, converted.longValue());
    }

    /**
     * Convert a fiat amount to a coin amount using this exchange rate.
     * @throws ArithmeticException if the converted coin amount is too high or too low.
     */
    public Coin fiatToCoin(Fiat convertFiat) {
        checkArgument(convertFiat.currencyCode.equals(fiat.currencyCode), "Currency mismatch: %s vs %s",
                convertFiat.currencyCode, fiat.currencyCode);
        // Use BigInteger because it's much easier to maintain full precision without overflowing.
        final BigInteger converted = BigInteger.valueOf(convertFiat.value).multiply(BigInteger.valueOf(coin.value))
                .divide(BigInteger.valueOf(fiat.value));
        if (converted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        try {
            return Coin.valueOf(converted.longValue(), NetworkParameters.BIGNETCOIN_TOKENID);
        } catch (IllegalArgumentException x) {
            throw new ArithmeticException("Overflow: " + x.getMessage());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRate other = (ExchangeRate) o;
        return Objects.equal(this.coin, other.coin) && Objects.equal(this.fiat, other.fiat);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coin, fiat);
    }
}
