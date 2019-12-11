package net.bigtangle.encrypt;

import static org.junit.Assert.assertArrayEquals;

import java.math.BigInteger;

import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import net.bigtangle.core.ECKey;

public class ECKeyEncryptTest {

    private byte[] payload = Hex.decode("1122334455");

    @Test
    public void importECKeyDecrypt() {
        BigInteger privKey = new BigInteger("5e173f6ac3c669587538e7727cf19b782a4f2fda07c1eaa662c593e5e85e3051", 16);
        ECKey ecKey = ECKey.fromPrivate(privKey);
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
        ECKey ecKey = new ECKey();
        try {
            byte[] cipher = ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
            byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
            assertArrayEquals(decryptedPayload, payload);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
