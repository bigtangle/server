/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.uri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.utils.MonetaryFormat;

public class BitcoinURITest {
	private BitcoinURI testObject = null;

	private static final NetworkParameters MAINNET = MainNetParams.get();
	private static final String MAINNET_GOOD_ADDRESS = "1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH";
	private static final String BITCOIN_SCHEME = MAINNET.getUriScheme();
	private static final MonetaryFormat NO_CODE = MonetaryFormat.FIAT.noCode();

	// TODO @Test
	public void testConvertToBitcoinURI() throws Exception {
		Address goodAddress = Address.fromBase58(MAINNET, MAINNET_GOOD_ADDRESS);

		// simple example
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello&message=AMessage",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("12.34"), "Hello", "AMessage"));

		// example with spaces, ampersand and plus
		assertEquals(
				"bitcoin:" + MAINNET_GOOD_ADDRESS
						+ "?amount=12.34&label=Hello%20World&message=Mess%20%26%20age%20%2B%20hope",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("12.34"), "Hello World",
						"Mess & age + hope"));

		// no amount, label present, message present
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello&message=glory",
				BitcoinURI.convertToBitcoinURI(goodAddress, null, "Hello", "glory"));

		// amount present, no label, message present
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("0.1"), null, "glory"));
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("0.1"), "", "glory"));

		// amount present, label present, no message
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("12.34"), "Hello", null));
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("12.34"), "Hello", ""));

		// amount present, no label, no message
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("1000"), null, null));
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000",
				BitcoinURI.convertToBitcoinURI(goodAddress, NO_CODE.parse("1000"), "", ""));

		// no amount, label present, no message
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello",
				BitcoinURI.convertToBitcoinURI(goodAddress, null, "Hello", null));

		// no amount, no label, message present
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha",
				BitcoinURI.convertToBitcoinURI(goodAddress, null, null, "Agatha"));
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha",
				BitcoinURI.convertToBitcoinURI(goodAddress, null, "", "Agatha"));

		// no amount, no label, no message
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, BitcoinURI.convertToBitcoinURI(goodAddress, null, null, null));
		assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, BitcoinURI.convertToBitcoinURI(goodAddress, null, "", ""));

	}

	@Test
	public void testGood_Simple() throws BitcoinURIParseException {
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS);
		assertNotNull(testObject);
		assertNull(testObject.getAmount(), "Unexpected amount");
		assertNull(testObject.getLabel(), "Unexpected label");
		assertEquals(20, testObject.getAddress().getHash160().length, "Unexpected label");
	}

	/**
	 * Test a broken URI (bad scheme)
	 */
	@Test
	public void testBad_Scheme() {
		try {
			testObject = new BitcoinURI(MAINNET, "blimpcoin:" + MAINNET_GOOD_ADDRESS);
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
		}
	}

	/**
	 * Test a broken URI (bad syntax)
	 */
	@Test
	public void testBad_BadSyntax() {
		// Various illegal characters
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + "|" + MAINNET_GOOD_ADDRESS);
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("Bad URI syntax"));
		}

		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "\\");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("Bad URI syntax"));
		}

		// Separator without field
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("Bad URI syntax"));
		}
	}

	/**
	 * Test a broken URI (missing address)
	 */
	@Test
	public void testBad_Address() {
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME);
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
		}
	}

	/**
	 * Handles a simple amount
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	// TODO @Test
	public void testGood_Amount() throws BitcoinURIParseException {
		// Test the decimal parsing
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=6543210.123");
		assertEquals("6543210123", testObject.getAmount().getValue() + "");

		// Test the decimal parsing
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=.12345678");
		assertEquals("12345678", testObject.getAmount().getValue() + "");

		// Test the integer parsing
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=6543210");
		assertEquals("654321000000000", testObject.getAmount().getValue() + "");
	}

	/**
	 * Handles a simple label
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testGood_Label() throws BitcoinURIParseException {
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label=Hello%20World");
		assertEquals("Hello World", testObject.getLabel());
	}

	/**
	 * Handles a simple label with an embedded ampersand and plus
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testGood_LabelWithAmpersandAndPlus() throws BitcoinURIParseException {
		String testString = "Hello Earth & Mars + Venus";
		String encodedLabel = BitcoinURI.encodeURLString(testString);
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label=" + encodedLabel);
		assertEquals(testString, testObject.getLabel());
	}

	/**
	 * Handles a Russian label (Unicode test)
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testGood_LabelWithRussian() throws BitcoinURIParseException {
		// Moscow in Russian in Cyrillic
		String moscowString = "\u041c\u043e\u0441\u043a\u0432\u0430";
		String encodedLabel = BitcoinURI.encodeURLString(moscowString);
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label=" + encodedLabel);
		assertEquals(moscowString, testObject.getLabel());
	}

	/**
	 * Handles a simple message
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testGood_Message() throws BitcoinURIParseException {
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?message=Hello%20World");
		assertEquals("Hello World", testObject.getMessage());
	}

	/**
	 * Handles a badly formatted amount field
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testBad_Amount() throws BitcoinURIParseException {
		// Missing
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("amount"));
		}

		// Non-decimal (BIP 21)
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=12X4");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("amount"));
		}
	}

	@Test
	public void testEmpty_Label() throws BitcoinURIParseException {
		assertNull(new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label=").getLabel());
	}

	@Test
	public void testEmpty_Message() throws BitcoinURIParseException {
		assertNull(new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?message=").getMessage());
	}

	/**
	 * Handles duplicated fields (sneaky address overwrite attack)
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testBad_Duplicated() throws BitcoinURIParseException {
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?address=aardvark");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("address"));
		}
	}

	@Test
	public void testGood_ManyEquals() throws BitcoinURIParseException {
		assertEquals("aardvark=zebra",
				new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?label=aardvark=zebra")
						.getLabel());
	}

	/**
	 * Handles unknown fields (required and not required)
	 * 
	 * @throws BitcoinURIParseException If something goes wrong
	 */
	@Test
	public void testUnknown() throws BitcoinURIParseException {
		// Unknown not required field
		testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?aardvark=true");
		assertEquals("BitcoinURI['aardvark'='true','address'='1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH']",
				testObject.toString());

		assertEquals("true", testObject.getParameterByName("aardvark"));

		// Unknown not required field (isolated)
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?aardvark");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("no separator"));
		}

		// Unknown and required field
		try {
			testObject = new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?req-aardvark=true");
			fail("Expecting BitcoinURIParseException");
		} catch (BitcoinURIParseException e) {
			assertTrue(e.getMessage().contains("req-aardvark"));
		}
	}

	@Test
	public void brokenURIs() throws BitcoinURIParseException {
		// Check we can parse the incorrectly formatted URIs produced by
		// blockchain.info and its iPhone app.
		String str = "bitcoin://1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH?amount=0.01000";
		BitcoinURI uri = new BitcoinURI(str);
		assertEquals("1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH", uri.getAddress().toString());
		assertEquals(Coin.COIN.divide(100), uri.getAmount());
	}

	// @Test(expected = BitcoinURIParseException.class)
	public void testBad_AmountTooPrecise() throws BitcoinURIParseException {
		new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=0.123456789");
	}

	@Test
	public void testBad_NegativeAmount() throws BitcoinURIParseException {
		assertThrows(BitcoinURIParseException.class, () -> {
			new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=-1");
		});

	}

	// NO MAX @Test(expected = BitcoinURIParseException.class)
	public void testBad_TooLargeAmount() throws BitcoinURIParseException {
		new BitcoinURI(MAINNET, BITCOIN_SCHEME + ":" + MAINNET_GOOD_ADDRESS + "?amount=100000000");
	}

	@Test
	public void testPaymentProtocolReq() throws Exception {
		// Non-backwards compatible form ...
		BitcoinURI uri = new BitcoinURI(MainNetParams.get(),
				"bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin%2Ff.php%3Fh%3Db0f02e7cea67f168e25ec9b9f9d584f9");
		assertEquals("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9",
				uri.getPaymentRequestUrl());
		assertEquals(ImmutableList.of("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9"),
				uri.getPaymentRequestUrls());
		assertNull(uri.getAddress());
	}

	@Test
	public void testMultiplePaymentProtocolReq() throws Exception {
		BitcoinURI uri = new BitcoinURI(MAINNET,
				"bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin&r1=bt:112233445566");
		assertEquals(ImmutableList.of("bt:112233445566", "https://bitcoincore.org/~gavin"),
				uri.getPaymentRequestUrls());
		assertEquals("https://bitcoincore.org/~gavin", uri.getPaymentRequestUrl());
	}

	@Test
	public void testNoPaymentProtocolReq() throws Exception {
		BitcoinURI uri = new BitcoinURI(MAINNET, "bitcoin:" + MAINNET_GOOD_ADDRESS);
		assertNull(uri.getPaymentRequestUrl());
		assertEquals(ImmutableList.of(), uri.getPaymentRequestUrls());
		assertNotNull(uri.getAddress());
	}

	@Test
	public void testUnescapedPaymentProtocolReq() throws Exception {
		BitcoinURI uri = new BitcoinURI(MainNetParams.get(),
				"bitcoin:?r=https://merchant.com/pay.php?h%3D2a8628fc2fbe");
		assertEquals("https://merchant.com/pay.php?h=2a8628fc2fbe", uri.getPaymentRequestUrl());
		assertEquals(ImmutableList.of("https://merchant.com/pay.php?h=2a8628fc2fbe"), uri.getPaymentRequestUrls());
		assertNull(uri.getAddress());
	}
}
