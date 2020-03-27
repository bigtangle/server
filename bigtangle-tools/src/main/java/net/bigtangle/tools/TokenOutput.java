package net.bigtangle.tools;

import java.io.File;
import java.util.HashMap;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class TokenOutput {
    public static NetworkParameters networkParameters = MainNetParams.get();

    public static void main(String[] args) throws  Exception{
        WalletAppKit walletAppKit1 = new WalletAppKit(networkParameters, new File("/home/cui/Downloads"), "201707040100000004");
        String url = "https://61.181.128.230:8088/";
        walletAppKit1.wallet().setServerURL(url);
 
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
