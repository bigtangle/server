package net.bigtangle.server.performance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.bigtangle.core.Block;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.docker.ResultExecution;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.checkpoint.DockerService;
import net.bigtangle.server.test.AbstractIntegrationTest;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CheckpointTest extends AbstractIntegrationTest {

    @Autowired
    private DockerService dockerService;
  

    //@Test
    public void testCreateDumpSQL() throws Exception { 
 
         ResultExecution re = dockerService.dockerExec(dockerService.mysqldumpCheck(1l)
                 ) ;
       log.debug(re.toString());
       String hash = re.getResult().split(" ")[0];
       assertTrue(hash.equals("034b92c696a4b33871e08ea238e6f3ad730eda8517e30de44823bcc8ce979f2f"));
    
    }

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