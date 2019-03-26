/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException.TimeTravelerException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.OutputsDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignAddressListResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignResponse;
import net.bigtangle.core.http.server.resp.SettingResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

public class APIIntegrationTests extends AbstractIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(APIIntegrationTests.class);

	public void testMultiSignByJson() throws Exception {
		List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
		MultiSignBy multiSignBy0 = new MultiSignBy();
		multiSignBy0.setTokenid("111111");
		multiSignBy0.setTokenindex(0);
		multiSignBy0.setAddress("222222");
		multiSignBy0.setPublickey("33333");
		multiSignBy0.setSignature("44444");
		multiSignBies.add(multiSignBy0);

		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);

		String jsonStr = Json.jsonmapper().writeValueAsString(multiSignByRequest);
		log.info(jsonStr);

		multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
		for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
			log.info(Json.jsonmapper().writeValueAsString(multiSignBy));
		}
	}

	@Test
	public void testClientVersion() throws Exception {
		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		String resp = OkHttp3Util.postString(contextRoot + ReqCmd.version.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		SettingResponse settingResponse = Json.jsonmapper().readValue(resp, SettingResponse.class);
		String version = settingResponse.getVersion();
		assertTrue(version.equals("0.3.3"));
	}

	@Test
	public void testCreateToken() throws JsonProcessingException, Exception {
		// Setup transaction and signatures
		wallet1();
		List<ECKey> keys = walletAppKit1.wallet().walletKeys(null);
		TokenInfo tokenInfo = new TokenInfo();
		testCreateMultiSigToken(keys.get(1),  "gold");
		testCreateMultiSigToken(keys.get(2),  "BTC");
		testCreateMultiSigToken(keys.get(3),  "ETH");
		testCreateMultiSigToken(keys.get(4),  "CNY");
		testCreateMultiSigToken(keys.get(7),  "人民币");
		testCreateMultiSigToken(keys.get(5),  "USD");
		testCreateMultiSigToken(keys.get(6),  "EUR");

	}

	@Test
	public void testCreateTokenYuan() throws JsonProcessingException, Exception {
		// Setup transaction and signatures
		wallet2();
		List<ECKey> keys = walletAppKit2.wallet().walletKeys(null);
		TokenInfo tokenInfo = new TokenInfo();
		testCreateMultiSigToken(keys.get(7),  "人民币");

	}

	public void testRequestBlock(Block block) throws Exception {

		HashMap<String, Object> requestParam = new HashMap<String, Object>();

		requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));

		byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		Block re = networkParameters.getDefaultSerializer().makeBlock(data);
		log.info("resp : " + re);

	}

	public Block nextBlockSerializer(ByteBuffer byteBuffer) {
		int len = byteBuffer.getInt();
		byte[] data = new byte[len];
		byteBuffer.get(data);
		Block r1 = networkParameters.getDefaultSerializer().makeBlock(data);
		log.debug("block len : " + len + " conv : " + r1.getHashAsString());
		return r1;
	}

	public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
		final Map<String, Object> request = new HashMap<String, Object>();
		byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(request));
		return data;
	}

	public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
		String data = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
		log.info("testSaveBlock resp : " + data);
		checkResponse(data);
	}

}
