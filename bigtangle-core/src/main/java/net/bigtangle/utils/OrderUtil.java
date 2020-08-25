package net.bigtangle.utils;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.response.OrderdataResponse;

public class OrderUtil {
    public static void orderMap(OrderdataResponse orderdataResponse, MonetaryFormat mf ,
            List<Map<String, Object>> orderData, Locale local) {
        
        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            HashMap<String, Object> map = new HashMap<String, Object>();

            
        if (orderRecord.getOrderBaseToken().equals(orderRecord.getOfferTokenid())) {
            Token t = orderdataResponse.getTokennames().get(orderRecord.getOrderBaseToken()); 
            map.put("type",  Side.BUY.name()) ;
            map.put("amount", mf.format(orderRecord.getTargetValue(), t.getDecimals()));
            map.put("tokenId", orderRecord.getTargetTokenid());

            map.put("price", mf.format(orderRecord.price(), t.getDecimals()));
            if (orderdataResponse.getTokennames() != null
                    && orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid()) != null) {
                map.put("tokenname",
                        orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid()).getTokennameDisplay());
            }
            map.put("total", mf.format(orderRecord.getOfferValue()));
        } else {
            Token t = orderdataResponse.getTokennames().get(orderRecord.getOrderBaseToken());
            map.put("type",   Side.SELL.name() );
            map.put("amount", mf.format(orderRecord.getOfferValue(), t.getDecimals()));
            map.put("tokenId", orderRecord.getOfferTokenid());
            map.put("price", mf.format(orderRecord.price(), t.getDecimals())); 
            map.put("total", mf.format(orderRecord.getTargetValue())); 
            if (orderdataResponse.getTokennames() != null
                    && orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid()) != null)
                map.put("tokenname",
                        orderdataResponse.getTokennames().get(orderRecord.getOfferTokenid()).getTokennameDisplay());
        }
        map.put("orderId", orderRecord.getBlockHashHex());
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", local);
        map.put("validateTo", dateFormat.format(new Date(orderRecord.getValidToTime() * 1000)));
        map.put("validatefrom", dateFormat.format(new Date(orderRecord.getValidFromTime() * 1000)));
        map.put("address", orderRecord.getBeneficiaryAddress());
        map.put("initialBlockHashHex", orderRecord.getBlockHashHex());
        map.put("orderBaseToken", orderRecord.getOrderBaseToken()); 
        map.put("cancelPending", orderRecord.isCancelPending());
        // map.put("state", Main.getText( (String)
        // requestParam.get("state")));
        orderData.add(map);
        
    }
    }
}
