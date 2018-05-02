package net.bigtangle.tools.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.bigtangle.core.Json;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class TokenPost extends ArrayList<String> {

    private static final long serialVersionUID = -8299620590125212324L;
    
    private static final TokenPost instance = new TokenPost();
    
    public static TokenPost getInstance() {
        return instance;
    }
    
    public void initialize() throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response = OkHttp3Util.post(Configure.CONTEXT_ROOT + "getTokens", Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("tokens");
        for (Map<String, Object> map : list) {
            String tokenHex = (String) map.get("tokenHex");
            this.add(tokenHex);
        }
    }

    public String randomTokenHex() {
        Random random = new Random();
        int index = random.nextInt(this.size());
        return this.get(index);
    }
}
