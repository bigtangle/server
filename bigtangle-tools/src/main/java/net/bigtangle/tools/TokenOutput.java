package net.bigtangle.tools;

import java.util.HashMap;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class TokenOutput {
    public static NetworkParameters networkParameters = MainNetParams.get();

    public static void main(String[] args) throws  Exception{
        Wallet wallet= new Wallet(networkParameters);
        String url = "https://61.181.128.230:8088/";
        wallet.setServerURL(url);
 
        String tokenid = "028a5b884f29d919abeddcb694e92186272029423a122315c5707cf21e1ebf63ab";
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", tokenid);
            String resp = OkHttp3Util.postString(url + ReqCmd.outputsOfTokenid.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
 
            for (UTXO u : getOutputsResponse.getOutputs()) {
                System.out.println(u);
                
            }
      

    
    } 
}
