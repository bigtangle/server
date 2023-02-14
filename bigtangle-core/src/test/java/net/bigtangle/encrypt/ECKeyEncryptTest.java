package net.bigtangle.encrypt;

import static org.junit.Assert.assertArrayEquals;

import java.math.BigInteger;
import java.util.Random;

import org.junit.Test;

import net.bigtangle.core.ECKey2;
import net.bigtangle.params.TestParams;

public class ECKeyEncryptTest {

    private byte[] payload =new String( new Random().nextLong()+"").getBytes();

  //  @Test
    public void importECKeyDecrypt() {
        BigInteger privKey = new BigInteger("5e173f6ac3c669587538e7727cf19b782a4f2fda07c1eaa662c593e5e85e3051", 16);
        ECKey2 ecKey = ECKey2.fromPrivate(privKey);
        try {
            byte[] cipher = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
            byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
            assertArrayEquals(decryptedPayload, payload);
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    @Test
    public void newECKeyDecrypt() {
        ECKey2 ecKey = new ECKey2();
        System.out.println("public= " +ecKey.getPublicKeyAsHex());
        System.out.println("public address= " +ecKey.toAddress(TestParams.get()));
        System.out.println("private= " +ecKey.getPrivateKeyAsHex());
        try {
            byte[] cipher = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
            byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
            assertArrayEquals(decryptedPayload, payload);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
