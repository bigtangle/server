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
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.utils.OkHttp3Util;
import okhttp3.OkHttpClient;

public class SendEmptyBlock {

    public static NetworkParameters params = MainNetParams.get();

    OkHttpClient client = new OkHttpClient();

    private static final Logger log = LoggerFactory.getLogger(SendEmptyBlock.class);

    public String CONTEXT_ROOT = "https://bigtangle.org/";

    // "http://localhost:8088/";//
    public static void main(String[] args) throws Exception {
        while (true) {
            SendEmptyBlock sendEmptyBlock = new SendEmptyBlock();
            int c = sendEmptyBlock.needEmptyBlocks(sendEmptyBlock.CONTEXT_ROOT);
            if (c > 0) {
                for (int i = 0; i < c; i++) {

                    try {
                        sendEmptyBlock.send();
                    } catch (JsonProcessingException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }

            }
            Thread.sleep(1000);
        }
    }

    public void send() throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block rollingBlock = params.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        OkHttp3Util.post(CONTEXT_ROOT + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

    }

    private int needEmptyBlocks(String server) throws Exception {
        List<BlockEvaluationDisplay> a = getBlockInfos(server);
        // only parallel blocks with rating < 70 need empty to resolve conflicts
        int res = 0;
        for (BlockEvaluationDisplay b : a) {
            if (b.getRating() < 70) {
                res += 1;
            }
        }
        // empty blocks
        return res ;
    }

    private List<BlockEvaluationDisplay> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = "200";
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

}
