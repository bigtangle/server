/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class SerializationTest {

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    @Test
    public void testContactInfoSerialization() throws IOException {
        ContactInfo info1 = new ContactInfo();
        info1.setVersion(3);
        final Contact e = new Contact();
        e.setAddress("test1");
        e.setName("test2");
        info1.getContactList().add(e);
        ContactInfo info2 = new ContactInfo().parse(info1.toByteArray());

        assertArrayEquals(info1.toByteArray(), info2.toByteArray());
        assertEquals(info1.getVersion(), info2.getVersion());
        assertEquals(info1.getContactList().get(0).getAddress(), info2.getContactList().get(0).getAddress());
        assertEquals(info1.getContactList().get(0).getName(), info2.getContactList().get(0).getName());
    }

    @Test
    public void testOrderOpenInfoSerialization() throws IOException {
        OrderOpenInfo info1 = new OrderOpenInfo(2l, "test1", getRandomSha256Hash().getBytes(), 3l, 4l, Side.SELL, "test2");
        OrderOpenInfo info2 = new OrderOpenInfo().parse(info1.toByteArray());

        assertArrayEquals(info1.toByteArray(), info2.toByteArray());
        assertEquals(info1.getBeneficiaryAddress(), info2.getBeneficiaryAddress());
        assertArrayEquals(info1.getBeneficiaryPubKey(), info2.getBeneficiaryPubKey());
        assertEquals(info1.getTargetTokenid(), info2.getTargetTokenid());
        assertEquals(info1.getTargetValue(), info2.getTargetValue());
        assertEquals(info1.getValidFromTime(), info2.getValidFromTime());
        assertEquals(info1.getValidToTime(), info2.getValidToTime());
        assertEquals(info1.getVersion(), info2.getVersion());
    }

    @Test
    public void testOrderCancelInfoSerialization() throws IOException {
        OrderCancelInfo info1 = new OrderCancelInfo(getRandomSha256Hash());
        OrderCancelInfo info2 = new OrderCancelInfo().parse(info1.toByteArray());

        assertArrayEquals(info1.toByteArray(), info2.toByteArray());
        assertEquals(info1.getBlockHash(), info2.getBlockHash());
    }

    @Test
    public void testMyHomeAddressSerialization() throws IOException {
        MyHomeAddress info1 = new MyHomeAddress();
        info1.setCity("test1");
        info1.setCountry("test2");
        info1.setEmail("test3");
        info1.setProvince("test4");
        info1.setRemark("test5");
        info1.setStreet("test6");
        MyHomeAddress info2 = new MyHomeAddress().parse(info1.toByteArray());

        assertArrayEquals(info1.toByteArray(), info2.toByteArray());
        assertEquals(info1.getCity(), info2.getCity());
        assertEquals(info1.getCountry(), info2.getCountry());
        assertEquals(info1.getEmail(), info2.getEmail());
        assertEquals(info1.getProvince(), info2.getProvince());
        assertEquals(info1.getRemark(), info2.getRemark());
        assertEquals(info1.getStreet(), info2.getStreet());
    }

    @Test
    public void testRewardInfoSerialization() throws IOException {
        Sha256Hash randomHash = getRandomSha256Hash();
        HashSet<Sha256Hash> blocks = new HashSet<Sha256Hash>();
        blocks.add(randomHash);
        RewardInfo info1 = new RewardInfo(randomHash, 2, blocks, 2l);
        byte[] bytes1 = info1.toByteArray();
        RewardInfo info2 = new RewardInfo().parse(bytes1);
        byte[] bytes2 = info2.toByteArray();

        assertArrayEquals(bytes1, bytes2);
        assertEquals(info1.getPrevRewardHash(), info2.getPrevRewardHash());
        assertEquals(info1.getChainlength(), info2.getChainlength());
        assertEquals(info1.getBlocks().toArray()[0], info2.getBlocks().toArray()[0]);
    }

    @Test
    public void testTokenInfoSerialization() throws IOException {
        List<MultiSignAddress> addresses = new ArrayList<>();
        Token tokens = Token.buildSimpleTokenInfo(true, null, "2", "3", "4", 2, 3, BigInteger.valueOf(4), true, 0, "de");
        TokenInfo info1 = new TokenInfo();
        info1.setToken(tokens);
        info1.setMultiSignAddresses(addresses);
        byte[] bytes1 = info1.toByteArray();
        TokenInfo info2 = new TokenInfo().parse(bytes1);
        byte[] bytes2 = info2.toByteArray();

        assertArrayEquals(bytes1, bytes2);
        assertEquals(info1.getMultiSignAddresses().size(), info2.getMultiSignAddresses().size());
        assertEquals(info1.getToken().getAmount(), info2.getToken().getAmount());
        assertEquals(info1.getToken().getBlockHash(), info2.getToken().getBlockHash());
        assertEquals(info1.getToken().getDescription(), info2.getToken().getDescription());
        assertEquals(info1.getToken().getPrevblockhash(), info2.getToken().getPrevblockhash());
        assertEquals(info1.getToken().getSignnumber(), info2.getToken().getSignnumber());
        assertEquals(info1.getToken().getTokenid(), info2.getToken().getTokenid());
        assertEquals(info1.getToken().getTokenindex(), info2.getToken().getTokenindex());
        assertEquals(info1.getToken().getTokenname(), info2.getToken().getTokenname());
        assertEquals(info1.getToken().getTokentype(), info2.getToken().getTokentype());
        assertEquals(info1.getToken().getDomainName(), info2.getToken().getDomainName());
        assertEquals(info1.getToken().isConfirmed(), info2.getToken().isConfirmed());

        assertEquals(info1.getToken().isTokenstop(), info2.getToken().isTokenstop());
    }
}
