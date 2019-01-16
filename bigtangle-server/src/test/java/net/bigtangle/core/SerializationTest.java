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
    	Token tokens = Token.buildSimpleTokenInfo(true, "1", "2", "3", "4", 2, 3, 4, true, true);
    	TokenInfo info1 = new TokenInfo();
    	info1.setTokens(tokens);
    	info1.setMultiSignAddresses(addresses);
    	byte[] bytes1 = info1.toByteArray();
    	TokenInfo info2 = TokenInfo.parse(bytes1);
    	byte[] bytes2 = info2.toByteArray();

    	assertArrayEquals(bytes1, bytes2);
    	assertEquals(info1.getMultiSignAddresses().size(), info2.getMultiSignAddresses().size());
    	assertEquals(info1.getTokens().getAmount(), info2.getTokens().getAmount());
    	assertEquals(info1.getTokens().getBlockhash(), info2.getTokens().getBlockhash());
    	assertEquals(info1.getTokens().getDescription(), info2.getTokens().getDescription());
    	assertEquals(info1.getTokens().getPrevblockhash(), info2.getTokens().getPrevblockhash());
    	assertEquals(info1.getTokens().getSignnumber(), info2.getTokens().getSignnumber());
    	assertEquals(info1.getTokens().getTokenid(), info2.getTokens().getTokenid());
    	assertEquals(info1.getTokens().getTokenindex(), info2.getTokens().getTokenindex());
    	assertEquals(info1.getTokens().getTokenname(), info2.getTokens().getTokenname());
    	assertEquals(info1.getTokens().getTokentype(), info2.getTokens().getTokentype());
    	assertEquals(info1.getTokens().getUrl(), info2.getTokens().getUrl());
    	assertEquals(info1.getTokens().isConfirmed(), info2.getTokens().isConfirmed());
    	assertEquals(info1.getTokens().isMultiserial(), info2.getTokens().isMultiserial());
    	assertEquals(info1.getTokens().isTokenstop(), info2.getTokens().isTokenstop());
    }
}
