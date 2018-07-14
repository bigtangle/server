package net.bigtangle.server;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.Json;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;

public class SimpleIntegrationTest {

    @Test
    public void testJsonMapper() throws Exception {
        GetOrderResponse getOrderResponse = new GetOrderResponse();
        getOrderResponse.setErrorcode(0);
        
        OrderPublish orderPublish = new OrderPublish("address", "tokenid", 0, null, null, 2000, 2000, "http://localhost");
        List<OrderPublish> orders = new ArrayList<>();
        orders.add(orderPublish);
        
        getOrderResponse.setOrders(orders);
        String jsonStr = Json.jsonmapper().writeValueAsString(getOrderResponse);
        System.out.println(jsonStr);
        
        GetOrderResponse getOrderResponse2 = Json.jsonmapper().readValue(jsonStr, GetOrderResponse.class);
        
        OrderPublish orderPublish2 = getOrderResponse2.getOrders().get(0);
        
        Class<?> clazz = orderPublish2.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            System.out.println("field : " + field.getName() + ", value : " + field.get(orderPublish2));
        }
    }
}
