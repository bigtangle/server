/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.COIN;
import static net.bigtangle.core.Coin.ZERO;
import static net.bigtangle.core.Coin.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.bigtangle.utils.MonetaryFormat;

public class CoinTest {

    @Test
    public void testParseCoin() {
        // String version
        assertEquals(Coin.COIN.divide(100), MonetaryFormat.FIAT.noCode().parse("0.01"));
     //   assertEquals(CENT, MonetaryFormat.FIAT.noCode().parse("1E-2"));
        assertEquals(COIN.add(Coin.COIN.divide(100)), MonetaryFormat.FIAT.noCode().parse("1.01"));
        assertEquals(COIN.negate(), MonetaryFormat.FIAT.noCode().parse("-1"));
        try {
            MonetaryFormat.FIAT.noCode().parse("2E-20");
            org.junit.Assert.fail("should not have accepted fractional satoshis");
        } catch (IllegalArgumentException expected) {
        } catch (Exception e) {
            org.junit.Assert.fail("should throw IllegalArgumentException");
        }
    }

    @Test
    public void testValueOf() {
        // int version
        // assertEquals(CENT, valueOf(0,
        // 1,NetworkParameters.BIGNETCOIN_TOKENID));
     
        valueOf(Long.MAX_VALUE, NetworkParameters.BIGTANGLE_TOKENID);
        valueOf(Long.MIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID);

     
    }

    @Test
    public void testOperators() {
   
        assertFalse(ZERO.isPositive());
        assertFalse(ZERO.isNegative());
        assertTrue(ZERO.isZero());

        assertTrue(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)
                .isGreaterThan(valueOf(1, NetworkParameters.BIGTANGLE_TOKENID)));
        assertFalse(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)
                .isGreaterThan(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)));
        assertFalse(valueOf(1, NetworkParameters.BIGTANGLE_TOKENID)
                .isGreaterThan(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)));
        assertTrue(valueOf(1, NetworkParameters.BIGTANGLE_TOKENID)
                .isLessThan(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)));
        assertFalse(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)
                .isLessThan(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)));
        assertFalse(valueOf(2, NetworkParameters.BIGTANGLE_TOKENID)
                .isLessThan(valueOf(1, NetworkParameters.BIGTANGLE_TOKENID)));
    }

    @Test(expected = ArithmeticException.class)
    public void testMultiplicationOverflow() {
        Coin.valueOf(Long.MAX_VALUE, NetworkParameters.BIGTANGLE_TOKENID).multiply(2);
    }

    @Test(expected = ArithmeticException.class)
    public void testMultiplicationUnderflow() {
        Coin.valueOf(Long.MIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID).multiply(2);
    }

    @Test(expected = ArithmeticException.class)
    public void testAdditionOverflow() {
        Coin.valueOf(Long.MAX_VALUE, NetworkParameters.BIGTANGLE_TOKENID).add(Coin.COIN);
    }

    @Test(expected = ArithmeticException.class)
    public void testSubstractionUnderflow() {
        Coin.valueOf(Long.MIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID).subtract(Coin.COIN);
    }
 
    /**
     * Test the bitcoinValueToPlainString amount formatter
     */
   // @Test
    public void testToPlainString() {
        assertEquals("0.15", Coin.valueOf(150000, NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("1.23", MonetaryFormat.FIAT.noCode().parse("1.23").toPlainString());

        assertEquals("0.1", MonetaryFormat.FIAT.noCode().parse("0.1").toPlainString());
        assertEquals("1.1", MonetaryFormat.FIAT.noCode().parse("1.1").toPlainString());
        assertEquals("21.12", MonetaryFormat.FIAT.noCode().parse("21.12").toPlainString());
        assertEquals("321.123", MonetaryFormat.FIAT.noCode().parse("321.123").toPlainString());
        assertEquals("4321.1234", MonetaryFormat.FIAT.noCode().parse("4321.1234").toPlainString());
        assertEquals("54321.12345", MonetaryFormat.FIAT.noCode().parse("54321.12345").toPlainString());
        assertEquals("654321.123456", MonetaryFormat.FIAT.noCode().parse("654321.123456").toPlainString());
        assertEquals("7654321.1234567",
                MonetaryFormat.FIAT.noCode().parse("7654321.1234567").toPlainString());
        assertEquals("87654321.12345678",
                MonetaryFormat.FIAT.noCode().parse("87654321.12345678").toPlainString());

        // check there are no trailing zeros
        assertEquals("1", MonetaryFormat.FIAT.noCode().parse("1.0").toPlainString());
        assertEquals("2", MonetaryFormat.FIAT.noCode().parse("2.00").toPlainString());
        assertEquals("3", MonetaryFormat.FIAT.noCode().parse("3.000").toPlainString());
        assertEquals("4", MonetaryFormat.FIAT.noCode().parse("4.0000").toPlainString());
        assertEquals("5", MonetaryFormat.FIAT.noCode().parse("5.00000").toPlainString());
        assertEquals("6", MonetaryFormat.FIAT.noCode().parse("6.000000").toPlainString());
        assertEquals("7", MonetaryFormat.FIAT.noCode().parse("7.0000000").toPlainString());
        assertEquals("8", MonetaryFormat.FIAT.noCode().parse("8.00000000").toPlainString());
    }
}
