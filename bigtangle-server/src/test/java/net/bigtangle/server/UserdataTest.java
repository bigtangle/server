package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserdataTest extends AbstractIntegrationTest {
    @Test
    public void testSaveUserData() throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_USERDATA);
        ECKey outKey = new ECKey();

        Transaction transaction = new Transaction(networkParameters);
        Contact contact = new Contact();
        contact.setName("testname");
        contact.setAddress(outKey.toAddress(networkParameters).toBase58());
        ContactInfo contactInfo0 = new ContactInfo();
        List<Contact> list = new ArrayList<Contact>();
        list.add(contact);
        contactInfo0.setContactList(list);

        transaction.setDataClassName(DataClassName.CONTACTINFO.name());
        // TODO encrypt and decrypt the contactInfo0
        transaction.setData(contactInfo0.toByteArray());

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(transaction);
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

        mcmcService.update();
        confirmationService.update();
        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        byte[] buf = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getUserData.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        ContactInfo contactInfo1 = new ContactInfo().parse(buf);
        assertTrue(contactInfo1.getContactList().size() == 1);

        Contact contact0 = contactInfo1.getContactList().get(0);
        assertTrue("testname".equals(contact0.getName()));

        transaction = new Transaction(networkParameters);
        contactInfo1.setContactList(new ArrayList<Contact>());
        transaction.setDataClassName(DataClassName.CONTACTINFO.name());
        transaction.setData(contactInfo1.toByteArray());

        sighash = transaction.getHash();
        party1Signature = outKey.sign(sighash);
        buf1 = party1Signature.encodeToDER();

        multiSignBies = new ArrayList<MultiSignBy>();
        multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        requestParam.clear();
        data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_USERDATA);

        block.addTransaction(transaction);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        mcmcService.update();
        confirmationService.update();
        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        buf = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getUserData.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        contactInfo1 = new ContactInfo().parse(buf);
        assertTrue(contactInfo1.getContactList().size() == 0);
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

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(transaction);
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

    }
}
