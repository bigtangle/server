package net.bigtangle.seeds;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegisterTest extends AbstractIntegrationTest {
	private static final Logger logger = LoggerFactory.getLogger(RegisterTest.class);

	@Test
	public void testURL() throws Exception {
		HashMap<String, String> requestParam = new HashMap<String, String>();
		requestParam.put("url", "https://bigtangle.de:8088");
		requestParam.put("servertype", "bigtangle");
		OkHttp3Util.post("https://81.169.156.203:8089/" + ReqCmd.register.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		requestParam.put("url", "https://bigtangle.de:8090");
		requestParam.put("servertype", "bigtangle");
		OkHttp3Util.post("https://81.169.156.203:8089/" + ReqCmd.register.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		requestParam = new HashMap<String, String>();
		byte[] data = OkHttp3Util.post("https://81.169.156.203:8089/" + ReqCmd.serverinfolist.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());
		ServerinfoResponse response = Json.jsonmapper().readValue(data, ServerinfoResponse.class);
		if (response.getServerInfoList() != null) {
			for (ServerInfo serverInfo : response.getServerInfoList()) {
				logger.info(serverInfo.getUrl() + "," + serverInfo.getServertype());
			}
		}
	}
	@Test
	public void testWalletUtil() throws Exception {

	}
	@Test
	public void testChainnumber() throws Exception {
		HashMap<String, String> requestParam = new HashMap<String, String>();
		requestParam.put("server", "https://bigtangle.de:8088");

		byte[] data = OkHttp3Util.post(getContextRoot() + ReqCmd.getChainNumber.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());
	}
}
