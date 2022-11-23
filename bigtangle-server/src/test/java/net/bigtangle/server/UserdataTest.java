package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UserSettingData;
import net.bigtangle.core.UserSettingDataInfo;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserdataTest extends AbstractIntegrationTest {
    @Test
    public void testSaveUserData() throws Exception {
      
        ECKey outKey = new ECKey();
        Transaction transaction = new Transaction(networkParameters);
        UserSettingData contact = new UserSettingData();
        contact.setDomain("contact");
        contact.setKey("testname");
        contact.setValue(outKey.toAddress(networkParameters).toBase58());
        UserSettingDataInfo contactInfo0 = new UserSettingDataInfo();
        List<UserSettingData> list = new ArrayList<UserSettingData>();
        list.add(contact);
        contactInfo0.setUserSettingDatas(list);
        // Token list displayname + tokenid

        transaction.setDataClassName(DataClassName.UserSettingDataInfo.name());
        transaction.setData(contactInfo0.toByteArray());
       
        
        // TODO encrypt and decrypt the  UserSettingData
        
        
        walletAppKit.wallet().saveUserdata(outKey, transaction,true);

        makeRewardBlock();

    
        UserSettingDataInfo contactInfo1 = walletAppKit.wallet().getUserSettingDataInfo(outKey,true);
        assertTrue(contactInfo1.getUserSettingDatas().size() == 1);

        UserSettingData contact0 = contactInfo1.getUserSettingDatas().get(0);
        assertTrue("testname".equals(contact0.getKey()));

        transaction = new Transaction(networkParameters);
        contactInfo1.setUserSettingDatas(new ArrayList<UserSettingData>());
        transaction.setDataClassName(DataClassName.UserSettingDataInfo.name());
        transaction.setData(contactInfo1.toByteArray());

        walletAppKit.wallet().saveUserdata(outKey, transaction,true);
        makeRewardBlock();
 

        contactInfo1 = walletAppKit.wallet().getUserSettingDataInfo(outKey,true);
        assertTrue(contactInfo1.getUserSettingDatas().size() == 0);
    }
    @Test
    public void testSaveUserDataWithECKey() throws Exception {

        ECKey outKey = new ECKey();
        Transaction transaction = new Transaction(networkParameters);
        UserSettingData contact = new UserSettingData();
        contact.setDomain("contact");
        contact.setKey("testname");
        contact.setValue(outKey.toAddress(networkParameters).toBase58());
        UserSettingDataInfo contactInfo0 = new UserSettingDataInfo();
        List<UserSettingData> list = new ArrayList<UserSettingData>();
        list.add(contact);
        contactInfo0.setUserSettingDatas(list);
        // Token list displayname + tokenid

        transaction.setDataClassName(DataClassName.UserSettingDataInfo.name());
        transaction.setData(contactInfo0.toByteArray());      
        
        walletAppKit.wallet().saveUserdata(outKey, transaction,true);

        makeRewardBlock();


        UserSettingDataInfo contactInfo1 =  walletAppKit.wallet().getUserSettingDataInfo(outKey,true);
        assertTrue(contactInfo1.getUserSettingDatas().size() == 1);

        UserSettingData contact0 = contactInfo1.getUserSettingDatas().get(0);
        assertTrue("testname".equals(contact0.getKey()));


    }
    @Test
    public void testServerURL() throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_USERDATA);
        ECKey outKey = new ECKey();

        Transaction transaction = new Transaction(networkParameters);
        Contact contact = new Contact();
        contact.setName("bigtangle.org");
        contact.setAddress(outKey.toAddress(networkParameters).toBase58());
        ContactInfo contactInfo0 = new ContactInfo();
        List<Contact> list = new ArrayList<Contact>();
        list.add(contact);
        contactInfo0.setContactList(list);

        transaction.setDataClassName(DataClassName.SERVERURL.name());
        transaction.setData(contactInfo0.toByteArray());
        // TODO encrypt and decrypt the contactInfo0
        walletAppKit.wallet().saveUserdata(outKey, transaction,false);

    }

 

}
