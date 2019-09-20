/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.tools;

import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.utils.OkHttp3Util;
import okhttp3.OkHttpClient;

public class StreamBlocks {

    public static NetworkParameters params = MainNetParams.get();

    OkHttpClient client = new OkHttpClient();

    // private String CONTEXT_ROOT = "http://bigtangle.net:8088/";

    private static String CONTEXT_ROOT = "https://bigtangle.org/";

    public static void main(String[] args) throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("heightstart", "1");
       // requestParam.put("kafka", "de.kafka.bigtangle.net:9092");
        OkHttp3Util.postAndGetBlock(CONTEXT_ROOT + ReqCmd.streamBlocks.name(), Json.jsonmapper().writeValueAsString(requestParam));

    }

}
