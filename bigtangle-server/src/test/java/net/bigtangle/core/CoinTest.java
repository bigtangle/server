/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.CENT;
import static net.bigtangle.core.Coin.COIN;
import static net.bigtangle.core.Coin.NEGATIVE_SATOSHI;
import static net.bigtangle.core.Coin.SATOSHI;
import static net.bigtangle.core.Coin.ZERO;
import static net.bigtangle.core.Coin.parseCoin;
import static net.bigtangle.core.Coin.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CoinTest {

    @Test
    public void testParseCoin() {
        // String version
        assertEquals(CENT, parseCoin("0.01", NetworkParameters.BIGTANGLE_TOKENID));
        assertEquals(CENT, parseCoin("1E-2", NetworkParameters.BIGTANGLE_TOKENID));
        assertEquals(COIN.add(CENT), parseCoin("1.01", NetworkParameters.BIGTANGLE_TOKENID));
        assertEquals(COIN.negate(), parseCoin("-1", NetworkParameters.BIGTANGLE_TOKENID));
        try {
            parseCoin("2E-20", NetworkParameters.BIGTANGLE_TOKENID);
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
        assertEquals(SATOSHI, valueOf(1, NetworkParameters.BIGTANGLE_TOKENID));
        assertEquals(NEGATIVE_SATOSHI, valueOf(-1, NetworkParameters.BIGTANGLE_TOKENID));
 
        valueOf(Long.MAX_VALUE, NetworkParameters.BIGTANGLE_TOKENID);
        valueOf(Long.MIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID);

     
    }

    @Test
    public void testOperators() {
        assertTrue(SATOSHI.isPositive());
        assertFalse(SATOSHI.isNegative());
        assertFalse(SATOSHI.isZero());
        assertFalse(NEGATIVE_SATOSHI.isPositive());
        assertTrue(NEGATIVE_SATOSHI.isNegative());
        assertFalse(NEGATIVE_SATOSHI.isZero());
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
        Coin.valueOf(Long.MAX_VALUE, NetworkParameters.BIGTANGLE_TOKENID).add(Coin.SATOSHI);
    }

    @Test(expected = ArithmeticException.class)
    public void testSubstractionUnderflow() {
        Coin.valueOf(Long.MIN_VALUE, NetworkParameters.BIGTANGLE_TOKENID).subtract(Coin.SATOSHI);
    }
 
    /**
     * Test the bitcoinValueToPlainString amount formatter
     */
   // @Test
    public void testToPlainString() {
        assertEquals("0.15", Coin.valueOf(150000, NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("1.23", parseCoin("1.23", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());

        assertEquals("0.1", parseCoin("0.1", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("1.1", parseCoin("1.1", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("21.12", parseCoin("21.12", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("321.123", parseCoin("321.123", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("4321.1234", parseCoin("4321.1234", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("54321.12345", parseCoin("54321.12345", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("654321.123456", parseCoin("654321.123456", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("7654321.1234567",
                parseCoin("7654321.1234567", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("87654321.12345678",
                parseCoin("87654321.12345678", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());

        // check there are no trailing zeros
        assertEquals("1", parseCoin("1.0", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("2", parseCoin("2.00", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("3", parseCoin("3.000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("4", parseCoin("4.0000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("5", parseCoin("5.00000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("6", parseCoin("6.000000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("7", parseCoin("7.0000000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
        assertEquals("8", parseCoin("8.00000000", NetworkParameters.BIGTANGLE_TOKENID).toPlainString());
    }
}
