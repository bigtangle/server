package net.bigtangle.json;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.utils.Json;

public class JsonMapperTest {

 

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
