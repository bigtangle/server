package net.bigtangle.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.response.OrderdataResponse;

public class OrderUtil {
    public static void orderMap(OrderdataResponse orderdataResponse, List<Map<String, Object>> orderData, Locale local,
            NetworkParameters params) {
        MonetaryFormat mf = MonetaryFormat.FIAT.noCode();

        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Token base = orderdataResponse.getTokennames().get(orderRecord.getOrderBaseToken());
            if (orderRecord.getOrderBaseToken().equals(orderRecord.getOfferTokenid())) {
                Token t = orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid());
                Integer priceshift = params.getOrderPriceShift(orderRecord.getOrderBaseToken());
                map.put("type", "buy");
                map.put("amount", mf.format(orderRecord.getTargetValue(), t.getDecimals()));
                map.put("tokenId", orderRecord.getTargetTokenid());

                map.put("price", mf.format(orderRecord.getPrice(), base.getDecimals() + priceshift));
                if (orderdataResponse.getTokennames() != null
                        && orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid()) != null) {
                    map.put("tokenname", orderdataResponse.getTokennames().get(orderRecord.getTargetTokenid())
                            .getTokennameDisplay());
                }
                map.put("total", mf.format(orderRecord.getOfferValue(), base.getDecimals()));
            } else {
                Token t = orderdataResponse.getTokennames().get(orderRecord.getOfferTokenid());
                map.put("type", "sell");
                map.put("amount", mf.format(orderRecord.getOfferValue(), t.getDecimals()));
                map.put("tokenId", orderRecord.getOfferTokenid());
                Integer priceshift = params.getOrderPriceShift(orderRecord.getOrderBaseToken());
                map.put("price", mf.format(orderRecord.getPrice(), base.getDecimals() + priceshift));

                map.put("total", mf.format(orderRecord.getTargetValue(), base.getDecimals()));

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
            map.put("orderBaseToken", base.getTokennameDisplay());
            map.put("cancelPending", orderRecord.isCancelPending());
            // map.put("state", Main.getText( (String)
            // requestParam.get("state")));
            orderData.add(map);

        }
    }

    public static Long calc(long m, long factor, long d) {
        return BigInteger.valueOf(m).multiply(BigInteger.valueOf(factor)).divide(BigInteger.valueOf(d)).longValue();
    }

    public static List<Map<String, Object>> resetOrderList(List<Map<String, Object>> orderList) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        Map<String, Map<String, List<Map<String, Object>>>> orderMap = new HashMap<String, Map<String, List<Map<String, Object>>>>();

        for (Map<String, Object> map : orderList) {
            String tokenname = map.get("tokenname").toString();
            String type = map.get("type").toString();
            if (!orderMap.containsKey(tokenname)) {
                orderMap.put(tokenname, new HashMap<String, List<Map<String, Object>>>());
            }
            if (!orderMap.get(tokenname).containsKey(type)) {
                orderMap.get(tokenname).put(type, new ArrayList<Map<String, Object>>());
            }

            orderMap.get(tokenname).get(type).add(map);
        }
        for (String tokenname : orderMap.keySet()) {
            Map<String, List<Map<String, Object>>> subMap = orderMap.get(tokenname);
            List<Map<String, Object>> buys = subMap.get("buy");
            if (buys != null) {
                Collections.sort(buys, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> order1, Map<String, Object> order2) {
                        BigDecimal price1 = new BigDecimal(order1.get("price").toString());
                        BigDecimal price2 = new BigDecimal(order2.get("price").toString());
                        return price1.compareTo(price2);
                    }
                });
            }

            List<Map<String, Object>> sells = subMap.get("sell");
            if (sells != null) {
                Collections.sort(sells, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> order1, Map<String, Object> order2) {
                        BigDecimal price1 = new BigDecimal(order1.get("price").toString());
                        BigDecimal price2 = new BigDecimal(order2.get("price").toString());
                        return price2.compareTo(price1);
                    }
                });
            }

            if ("BIG".equals(tokenname)) {
                if (buys != null && !buys.isEmpty()) {
                    list.addAll(0, buys);
                    if (sells != null && !sells.isEmpty()) {
                        list.addAll(buys.size(), sells);
                    }
                } else {
                    if (sells != null && !sells.isEmpty()) {
                        list.addAll(0, sells);
                    }
                }

            } else {
                if (buys != null && !buys.isEmpty()) {
                    list.addAll(buys);
                    if (sells != null && !sells.isEmpty()) {
                        list.addAll(sells);
                    }
                } else {
                    if (sells != null && !sells.isEmpty()) {
                        list.addAll(sells);
                    }
                }
            }
        }
        return list;
    }

}
