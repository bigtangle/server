package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class TokenSign {
    public static NetworkParameters networkParameters = MainNetParams.get();

    public static void main(String[] args) throws Exception {
        Wallet wallet = new Wallet(networkParameters);
        String url = "https://61.181.128.230:8088/";
        wallet.setServerURL(url);

        String tokenid = "03d109174d7b8aaab67d4090e58cde8a69906f85a292d26333f04ac81d99371798";
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(url + ReqCmd.getTokenSignByTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        SearchMultiSignResponse searchMultiSignResponse = Json.jsonmapper().readValue(resp,
                SearchMultiSignResponse.class);
        //System.out.println(searchMultiSignResponse.getMultiSignList());
        for (Map<String, Object> map : searchMultiSignResponse.getMultiSignList()) {
     

            byte[] payloadBytes = Utils.HEX.decode((String) map.get("blockhashHex"));
            Block block = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            System.out.println(block);
            Transaction transaction = block.getTransactions().get(0);

            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            for (  MultiSignBy m: multiSignByRequest.getMultiSignBies()) {
            System.out.println(m);
            }
        }
        for (Map<String, Object> map : searchMultiSignResponse.getMultiSignList()) {  
            System.out.println("tokenid=" +   map.get("tokenid" ));
            System.out.println("tokenindex=" +   map.get("tokenindex" ));
            System.out.println("sign=" +   map.get("sign" ));
            System.out.println("address=" +   map.get("address" ));
            System.out.println("signnumber=" +   map.get("signnumber" ));         }
    }

}
