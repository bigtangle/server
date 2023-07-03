package net.bigtangle.web.test;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import net.bigtangle.web.Zip;

public class WebTest extends AbstractIntegrationTest {

	protected static final Logger log = LoggerFactory.getLogger(WebTest.class);
	ECKey contractKey;

	public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
	public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
	String serverurl = "http://localhost:8088/";

	@Test
	public void testWebTokens() throws JsonProcessingException, Exception {
		contractKey = new ECKey();
		NetworkParameters networkParameters = TestParams.get();
		Wallet wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)), contextRoot);
		wallet.setServerURL(serverurl);
		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("site");
		// site contents zip
		byte[] zipFile=FileUtils.readFileToByteArray(new File("d:/test.zip"));
		String zipString=Base64.encodeBase64String(zipFile);
		kv.setValue(zipString);

		tokenKeyValues.addKeyvalue(kv);

		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), true,
				tokenKeyValues, TokenType.web.ordinal(), contractKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(contractKey.getPublicKeyAsHex(), signkey, null);
		byte[] zipFile = Base64.decodeBase64("zipcontent");
		SyncBlockService.byte2File(zipFile, "/var/www/", "contractlottery" + ".zip");
		new Zip().unZip("/var/www/" + "contractlottery" + ".zip");
		StringBuffer stringBuffer = new StringBuffer();
		
		stringBuffer.append("<VirtualHost *:80>\n");
		stringBuffer.append("ServerName contractlottery.bigtangle.org\n");
		stringBuffer.append("DocumentRoot /var/www/contractlottery\n");
		stringBuffer.append("ErrorLog ${APACHE_LOG_DIR}/error.log\n");
		stringBuffer.append("CustomLog ${APACHE_LOG_DIR}/access.log combined\n");
		stringBuffer.append("RewriteEngine on\n");
		stringBuffer.append("RewriteCond %{SERVER_NAME} =contractlottery.bigtangle.org\n");
		stringBuffer.append("RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]\n");
		stringBuffer.append("</VirtualHost>\n");
		
		stringBuffer.append("<VirtualHost *:443>\n");
		stringBuffer.append("ServerName  contractlottery.bigtangle.org\n");
		stringBuffer.append("DocumentRoot /var/www/contractlottery\n");
		stringBuffer.append("ErrorLog ${APACHE_LOG_DIR}/error.log\n");
		stringBuffer.append("CustomLog ${APACHE_LOG_DIR}/access.log combined\n");
		stringBuffer.append("SSLProxyEngine on\n");
		stringBuffer.append("SSLProxyVerify none\n");
		stringBuffer.append("SSLProxyCheckPeerCN off\n");
		stringBuffer.append("SSLProxyCheckPeerName off\n");
		stringBuffer.append("SSLProxyCheckPeerExpire off\n");
		stringBuffer.append("    SSLEngine on\n");
		stringBuffer.append("    SSLOptions +StrictRequire\n");
		stringBuffer.append("SSLProxyVerify none\n");
		stringBuffer.append("SSLProxyCheckPeerCN off\n");
		stringBuffer.append(" <Directory />\n");
		stringBuffer.append("SSLRenegBufferSize 2098200000\n");
		stringBuffer.append("SSLRequireSSL\n");
		stringBuffer.append("</Directory>\n");
		stringBuffer.append(" SSLCipherSuite HIGH:MEDIUM:!aNULL:+SHA1:+MD5:+HIGH:+MEDIUM\n");
		stringBuffer.append(" SSLSessionCacheTimeout 600\n");
		stringBuffer.append("SSLProxyEngine on\n");
		stringBuffer.append("Include /etc/letsencrypt/options-ssl-apache.conf\n");
		stringBuffer.append("SSLCertificateFile /etc/letsencrypt/live/www.bigtangle.org/fullchain.pem\n");
		stringBuffer.append("SSLCertificateKeyFile /etc/letsencrypt/live/www.bigtangle.org/privkey.pem\n");
		stringBuffer.append("</VirtualHost>");
		SyncBlockService.byte2File(stringBuffer.toString().getBytes(), "/var/www/", "contractlottery.bigtangle.org" + ".conf");
		
		

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
