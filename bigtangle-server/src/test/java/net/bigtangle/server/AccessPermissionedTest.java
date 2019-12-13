package net.bigtangle.server;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.SessionRandomNumResponse;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccessPermissionedTest extends AbstractIntegrationTest {

    @Test
    public void checkAccessPermissionedPubKeySign() throws JsonProcessingException, IOException, InvalidCipherTextException {
        ECKey ecKey = new ECKey();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKey", ecKey.getPublicKeyAsHex());
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.getSessionRandomNum.name(),
                Json.jsonmapper().writeValueAsBytes(requestParam));
        SessionRandomNumResponse sessionRandomNumResponse = Json.jsonmapper().readValue(resp, SessionRandomNumResponse.class);

        // eckey sign
        byte[] payload = Utils.HEX.decode(sessionRandomNumResponse.getVerifyHex());
        byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), payload);
        final String accessToken = Utils.HEX.encode(decryptedPayload);
        System.out.println("accessToken : " + accessToken);
        
        Sha256Hash hash = Sha256Hash.wrap(decryptedPayload);
        ECKey.ECDSASignature party1Signature = ecKey.sign(hash);
        byte[] signature = party1Signature.encodeToDER();
        
        System.out.println("pubKey : " + ecKey.getPublicKeyAsHex());
        System.out.println("signature : " + Utils.HEX.encode(signature));
        System.out.println("accessToken : " + Utils.HEX.encode(hash.getBytes()));
        
        final String header = ecKey.getPublicKeyAsHex() + "," + Utils.HEX.encode(signature) + "," + Utils.HEX.encode(hash.getBytes());
        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("name", null);
        resp = OkHttp3Util.post(contextRoot + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam00).getBytes(), header);
        System.out.println(resp);
    }

}
