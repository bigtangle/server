package net.bigtangle.server.performance;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.wallet.Wallet;

public class WebTest extends LotteryTests {

	protected static final Logger log = LoggerFactory.getLogger(WebTest.class);
	ECKey contractKey;

	public void testWebTokens() throws JsonProcessingException, Exception {

		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("site");
		// site contents zip
		byte[] zipFile = readStreamTobyte(new FileInputStream("d:/java/web.zip"));

		String zipString = Base64.encodeBase64String(zipFile);
		kv.setValue(zipString);

		tokenKeyValues.addKeyvalue(kv);

		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), true,
				tokenKeyValues, TokenType.web.ordinal(), contractKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(contractKey.getPublicKeyAsHex(), signkey, null);

		makeRewardBlock();
	}

	public static byte[] readStreamTobyte(InputStream inputStream) throws MalformedURLException {
		BufferedInputStream in = null;
		ByteArrayOutputStream out = null;
		try {
			in = new BufferedInputStream(inputStream);
			out = new ByteArrayOutputStream(1024);
			byte[] temp = new byte[1024];
			int size = 0;
			while ((size = in.read(temp)) != -1) {
				out.write(temp, 0, size);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// e.printStackTrace();
			}
		}
		byte[] content = out.toByteArray();
		return content;
	}

	/*
	 * pay money to the contract
	 */
	public void payContract(List<ECKey> userkeys, List<Block> blocks) throws Exception {
		for (ECKey key : userkeys) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			blocks.add(w.payContract(null, yuanTokenPub, winnerAmount, null, null, contractKey.getPublicKeyAsHex()));
		}
	}

}
