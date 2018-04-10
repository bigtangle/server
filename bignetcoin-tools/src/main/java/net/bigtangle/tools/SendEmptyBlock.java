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

import java.nio.ByteBuffer;
import java.util.HashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.squareup.okhttp.OkHttpClient;

import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.utils.OkHttp3Util;

public class SendEmptyBlock {

    public static NetworkParameters params = UnitTestParams.get();

    OkHttpClient client = new OkHttpClient();

   private String CONTEXT_ROOT = "http://bigtangle.net:8088/";
   //F  private String CONTEXT_ROOT = "http://localhost:8088/";
    public static void main(String[] args) {
        SendEmptyBlock sendEmptyBlock = new SendEmptyBlock();
        boolean c = true;
        while (c) {

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

    public void send() throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(CONTEXT_ROOT + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));

        Block rollingBlock = params.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

      String res = OkHttp3Util.post(CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());
      System.out.print("saveBlock"+ res+ rollingBlock);
    }
 

}
