package net.bigtangle.seeds;

import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegisterTest extends AbstractIntegrationTest {
	private static final Logger logger = LoggerFactory.getLogger(RegisterTest.class);

	@Test
	public void testURL() throws Exception {
		HashMap<String, String> requestParam = new HashMap<String, String>();
		requestParam.put("url", "http://hq.sinajs.cn/list=gb_didi");
		requestParam.put("servertype", "bigtangle");
		byte[] data = OkHttp3Util.post(getContextRoot() + ReqCmd.register.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		requestParam = new HashMap<String, String>();
		data = OkHttp3Util.post(getContextRoot() + ReqCmd.serverinfolist.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());
		ServerinfoResponse response = Json.jsonmapper().readValue(data, ServerinfoResponse.class);
		if (response.getServerInfoList() != null) {
			for (ServerInfo serverInfo : response.getServerInfoList()) {
				logger.info(serverInfo.getUrl() + "," + serverInfo.getServertype());

			}
		}
	}

}
