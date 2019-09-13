/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;

import org.junit.Test;

import com.kenai.jffi.Array;

import net.bigtangle.core.Utils;
import net.bigtangle.server.utils.Gzip;

import static org.junit.Assert.*;

public class UtilsTest {

    @Test
    public void testReverseBytes() {
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, Utils.reverseBytes(new byte[] { 5, 4, 3, 2, 1 }));
    }

    @Test
    public void testReverseDwordBytes() {
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 },
                Utils.reverseDwordBytes(new byte[] { 4, 3, 2, 1, 8, 7, 6, 5 }, -1));
        assertArrayEquals(new byte[] { 1, 2, 3, 4 }, Utils.reverseDwordBytes(new byte[] { 4, 3, 2, 1, 8, 7, 6, 5 }, 4));
        assertArrayEquals(new byte[0], Utils.reverseDwordBytes(new byte[] { 4, 3, 2, 1, 8, 7, 6, 5 }, 0));
        assertArrayEquals(new byte[0], Utils.reverseDwordBytes(new byte[0], 0));
    }

    @Test
    public void testMaxOfMostFreq() throws Exception {
        assertEquals(0, Utils.maxOfMostFreq());
        assertEquals(0, Utils.maxOfMostFreq(0, 0, 1));
        assertEquals(2, Utils.maxOfMostFreq(1, 1, 2, 2));
        assertEquals(1, Utils.maxOfMostFreq(1, 1, 2, 2, 1));
        assertEquals(-1, Utils.maxOfMostFreq(-1, -1, 2, 2, -1));
    }

    @Test
    public void compactEncoding() throws Exception {
        assertEquals(new BigInteger("1234560000", 16), Utils.decodeCompactBits(0x05123456L));
        assertEquals(new BigInteger("c0de000000", 16), Utils.decodeCompactBits(0x0600c0de));
        assertEquals(0x05123456L, Utils.encodeCompactBits(new BigInteger("1234560000", 16)));
        assertEquals(0x0600c0deL, Utils.encodeCompactBits(new BigInteger("c0de000000", 16)));
    }

    @Test
    public void dateTimeFormat() {
        assertEquals("2014-11-16T10:54:33Z", Utils.dateTimeFormat(1416135273781L));
        assertEquals("2014-11-16T10:54:33Z", Utils.dateTimeFormat(new Date(1416135273781L)));
    }

    @Test
    public void gzip() throws IOException {
       byte[] b = "Hallo".getBytes("UTF-8");
        assertTrue( Arrays.equals( Gzip.decompress(Gzip.compress(b)) ,b) );

    }

}
