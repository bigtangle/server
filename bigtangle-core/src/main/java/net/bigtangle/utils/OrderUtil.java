package net.bigtangle.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.response.OrderdataResponse;

public class OrderUtil {
    public static void orderMap(OrderdataResponse orderdataResponse, List<MarketOrderItem> orderData, Locale local,
            NetworkParameters params, String buy, String sell) {
        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            MarketOrderItem marketOrderItem = MarketOrderItem.build(orderRecord, orderdataResponse.getTokennames(),
                    params, buy, sell);
            orderData.add(marketOrderItem);

        }
    }

    public static Long calc(long m, long factor, long d) {
        return BigInteger.valueOf(m).multiply(BigInteger.valueOf(factor)).divide(BigInteger.valueOf(d)).longValue();
    }

    public static List<MarketOrderItem> resetOrderList(List<MarketOrderItem> orderList) {
        List<MarketOrderItem> list = new ArrayList<MarketOrderItem>();

        Map<String, Map<String, List<MarketOrderItem>>> orderMap = new HashMap<String, Map<String, List<MarketOrderItem>>>();

        for (MarketOrderItem map : orderList) {
            String tokenname = map.getTokenName();
            String type = map.getType();
            if (!orderMap.containsKey(tokenname)) {
                orderMap.put(tokenname, new HashMap<String, List<MarketOrderItem>>());
            }
            if (!orderMap.get(tokenname).containsKey(type)) {
                orderMap.get(tokenname).put(type, new ArrayList<MarketOrderItem>());
            }

            orderMap.get(tokenname).get(type).add(map);
        }
        for (String tokenname : orderMap.keySet()) {
            Map<String, List<MarketOrderItem>> subMap = orderMap.get(tokenname);
            List<MarketOrderItem> buys = subMap.get("buy");
            if (buys != null) {
                Collections.sort(buys, new Comparator<MarketOrderItem>() {
                    @Override
                    public int compare(MarketOrderItem order1, MarketOrderItem order2) {
                        BigDecimal price1 = new BigDecimal(order1.getPrice());
                        BigDecimal price2 = new BigDecimal(order2.getPrice());
                        return price2.compareTo(price1);
                    }
                });
            }

            List<MarketOrderItem> sells = subMap.get("sell");
            if (sells != null) {
                Collections.sort(sells, new Comparator<MarketOrderItem>() {
                    @Override
                    public int compare(MarketOrderItem order1, MarketOrderItem order2) {
                        BigDecimal price1 = new BigDecimal(order1.getPrice());
                        BigDecimal price2 = new BigDecimal(order2.getPrice());
                        return price1.compareTo(price2);
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
