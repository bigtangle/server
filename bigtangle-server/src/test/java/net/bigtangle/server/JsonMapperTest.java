package net.bigtangle.server;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import net.bigtangle.core.Coin;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;

public class JsonMapperTest {

    @Test
    public void testJsonMapperListObject() throws Exception {
        GetOrderResponse getOrderResponse = new GetOrderResponse();
        getOrderResponse.setErrorcode(0);

        OrderPublish orderPublish = new OrderPublish("address", "tokenid", 0, null, null, 2000, 2000,
                "http://localhost");
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

    @Test
    public void testJsonMapperByteList() throws Exception {
        ByteListResp byteListResp = new ByteListResp();
        ByteResp byteResp = new ByteResp();
        byteResp.setData(new byte[] { 0x00, 0x01, 0x02, 0x03 });
        byteListResp.getList().add(byteResp);
        String jsonStr = Json.jsonmapper().writeValueAsString(byteListResp);
        
        System.out.println(jsonStr);
        byteListResp = Json.jsonmapper().readValue(jsonStr, ByteListResp.class);
        
        byteResp = byteListResp.getList().get(0);
        for (byte b : byteResp.getData()) {
            System.out.println(String.format("%02X", b));
        }
    }
    
    @Test
    public void testJsonMapperSha256Hash() throws Exception {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        System.out.println(Utils.HEX.encode(sha256Hash.getBytes()));
    	String jsonStr = Json.jsonmapper().writeValueAsString(sha256Hash);
    	
    	sha256Hash = Json.jsonmapper().readValue(jsonStr, Sha256Hash.class);
    	System.out.println(Utils.HEX.encode(sha256Hash.getBytes()));
    	
    	Coin coin = Coin.valueOf(10000, NetworkParameters.BIGTANGLE_TOKENID);
    	jsonStr = Json.jsonmapper().writeValueAsString(coin);
    	
    	System.out.println(jsonStr);
    	
    	coin = Json.jsonmapper().readValue(jsonStr, Coin.class);
    	System.out.println(coin);
    }
}
