package net.bigtangle.web.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.params.TestParams;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.web.SyncBlockService;

// to run the test, it must start a server = "http://localhost:8088/"
public class WebTest extends AbstractIntegrationTest {

	protected static final Logger log = LoggerFactory.getLogger(WebTest.class);
	ECKey contractKey;

	public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
	public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
	String serverurl = "http://localhost:8088/";
	@Autowired
	SyncBlockService syncBlockService;

	@Test
	public void testWebTokens() throws JsonProcessingException, Exception {

		// File zip = new File("./src/test/resources/test.zip");

		// publishWebToken(zip);
		deployWebFile();
	}

	public void publishWebToken(File zip) throws Exception {
		contractKey = new ECKey();
		NetworkParameters networkParameters = TestParams.get();

		Wallet wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)), serverurl);
		wallet.setServerURL(serverurl);
		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("site");

		byte[] zipFile = FileUtils.readFileToByteArray(zip);
		String zipString = Base64.encodeBase64String(zipFile);
		kv.setValue(zipString);

		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("aliasService");
		kv.setValue("mytest");
		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), true,
				tokenKeyValues, TokenType.web.ordinal(), contractKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(contractKey.getPublicKeyAsHex(), signkey, null);
	}

	public void deployWebFile() throws Exception {
		String zipDirString = new File("./logs").getAbsolutePath() + "/test/";

		SyncBlockService.localFileServerInfoWrite(zipDirString, zipDirString, serverurl, true);
		File unzipDir = new File(zipDirString);

		for (String name : unzipDir.list()) {
			log.debug(name);
		}
		for (File file : unzipDir.listFiles()) {
			log.debug(file.getName());
		}
		assertTrue(unzipDir.exists() && unzipDir.isDirectory());
	}

	public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
			BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
			Wallet w) throws Exception {
		w.importKey(key);
		Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
				amount, !increment, decimals, "");
		token.setTokenKeyValues(tokenKeyValues);
		token.setTokentype(tokentype);
		List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
		addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
		return w.createToken(key, domainname, increment, token, addresses);

	}

}
