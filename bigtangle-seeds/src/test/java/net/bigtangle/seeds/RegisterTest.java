package net.bigtangle.seeds;

import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.response.GetStringResponse;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegisterTest extends AbstractIntegrationTest {
    
    @Test
    public void testURL() throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("url", "http://hq.sinajs.cn/list=gb_didi");
        byte[] data = OkHttp3Util.postString( getContextRoot() + ReqCmd.register.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetStringResponse  resp  = Json.jsonmapper().readValue(data,  GetStringResponse.class);
       
        String[] r = (resp.getText()).split(",");
    }

 

}
