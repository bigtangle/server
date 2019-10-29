package net.bigtangle.tools.test;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.response.OrderTickerResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class PriceTicker extends HelpTest {

    // cancel all orders

    @Test
    public void priceticker() throws Exception {

        List<String> tokenids = new ArrayList<String>();
    
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenids", tokenids);
        String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes()); 
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        MonetaryFormat mf = MonetaryFormat.FIAT.noCode();
        for (MatchResult matchResult : orderTickerResponse.getTickers()) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            // map.put("side",
            // getLocalMessage(matchResult.getIncomingSide().name().toLowerCase()));

            Token t = orderTickerResponse.getTokennames().get(matchResult.getTokenid());

            String price = MonetaryFormat.FIAT.noCode().format(matchResult.getPrice(),
                    orderTickerResponse.getTokennames().get(matchResult.getTokenid()).getDecimals());
            map.put("price", price);
            map.put("tokenid", matchResult.getTokenid());
            map.put("inserttime", dateFormat.format(new Date(matchResult.getInserttime())));
            map.put("executedQuantity", matchResult.getExecutedQuantity());
            // map.put("remainingQuantity", matchResult.getRemainingQuantity());


            map.put("tokenname", orderTickerResponse.getTokennames().get(matchResult.getTokenid()).getTokenname());

        }

    }

}
