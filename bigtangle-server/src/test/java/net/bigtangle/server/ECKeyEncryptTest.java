package net.bigtangle.server;

import org.ethereum.crypto.ECIESCoder;
import org.junit.Test;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.encoders.Hex;

import net.bigtangle.core.ECKey;


public class ECKeyEncryptTest {
    
    @Test  // encrypt decrypt round trip
    public void test2(){

        //BigInteger privKey = new BigInteger("5e173f6ac3c669587538e7727cf19b782a4f2fda07c1eaa662c593e5e85e3051", 16);

        byte[] payload = Hex.decode("1122334455");

        //ECKey ecKey =  ECKey.fromPrivate(privKey);
        
        ECKey ecKey = new ECKey();
        
        ECPoint pubKeyPoint = ecKey.getPubKeyPoint();

        byte[] cipher = new byte[0];
        try {
            cipher = ECIESCoder.encrypt(pubKeyPoint, payload);
        } catch (Throwable e) {e.printStackTrace();}

        System.out.println(Hex.toHexString(cipher));

        byte[] decrypted_payload = new byte[0];
        try {
            decrypted_payload = ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
        } catch (Throwable e) {e.printStackTrace();}

        System.out.println(Hex.toHexString(decrypted_payload));
    }
   
}
