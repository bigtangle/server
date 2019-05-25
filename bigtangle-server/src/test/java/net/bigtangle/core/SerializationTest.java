/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class SerializationTest {

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    @Test
    public void testRewardInfoSerialization() throws JsonParseException, JsonMappingException, IOException {
    	Sha256Hash randomHash = getRandomSha256Hash();
    	RewardInfo info1 = new RewardInfo(123, 456, randomHash);
    	byte[] bytes1 = info1.toByteArray();
    	RewardInfo info2 = RewardInfo.parse(bytes1);
    	byte[] bytes2 = info2.toByteArray();

    	assertArrayEquals(bytes1, bytes2);
    	assertEquals(info1.getFromHeight(), info2.getFromHeight());
    	assertEquals(info1.getPrevRewardHash(), info2.getPrevRewardHash());
    	assertEquals(info1.getToHeight(), info2.getToHeight());
    }

    @Test
    public void testTokenInfoSerialization() throws JsonParseException, JsonMappingException, IOException {
    	List<MultiSignAddress> addresses = new ArrayList<>();
    	Token tokens = Token.buildSimpleTokenInfo(true, "1", "2", "3", "4", 2, 3, 4, true);
    	TokenInfo info1 = new TokenInfo();
    	info1.setToken(tokens);
    	info1.setMultiSignAddresses(addresses);
    	byte[] bytes1 = info1.toByteArray();
    	TokenInfo info2 = TokenInfo.parse(bytes1);
    	byte[] bytes2 = info2.toByteArray();

    	assertArrayEquals(bytes1, bytes2);
    	assertEquals(info1.getMultiSignAddresses().size(), info2.getMultiSignAddresses().size());
    	assertEquals(info1.getToken().getAmount(), info2.getToken().getAmount());
    	assertEquals(info1.getToken().getBlockhash(), info2.getToken().getBlockhash());
    	assertEquals(info1.getToken().getDescription(), info2.getToken().getDescription());
    	assertEquals(info1.getToken().getPrevblockhash(), info2.getToken().getPrevblockhash());
    	assertEquals(info1.getToken().getSignnumber(), info2.getToken().getSignnumber());
    	assertEquals(info1.getToken().getTokenid(), info2.getToken().getTokenid());
    	assertEquals(info1.getToken().getTokenindex(), info2.getToken().getTokenindex());
    	assertEquals(info1.getToken().getTokenname(), info2.getToken().getTokenname());
    	assertEquals(info1.getToken().getTokentype(), info2.getToken().getTokentype());
    	assertEquals(info1.getToken().getDomainname(), info2.getToken().getDomainname());
    	assertEquals(info1.getToken().isConfirmed(), info2.getToken().isConfirmed());
     
    	assertEquals(info1.getToken().isTokenstop(), info2.getToken().isTokenstop());
    }
}
