/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.google.protobuf.ByteString;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletUtil;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.TestParams;

public class WalletUtilTest {

    private static final Logger log = LoggerFactory.getLogger(WalletUtilTest.class);

    @Test
    public void walletCreateLoadTest() throws Exception {

        byte[] a = WalletUtil.createWallet(TestParams.get());
        Wallet wallet = WalletUtil.loadWallet(false, new ByteArrayInputStream(a), MainNetParams.get());

        List<ECKey> issuedKeys = wallet.walletKeys(null);
        assertTrue(issuedKeys.size() == 1);
        for (ECKey ecKey : issuedKeys) {
            log.debug(ecKey.getPublicKeyAsHex());
            log.debug(ecKey.getPrivateKeyAsHex());
            log.debug(ecKey.toAddress(MainNetParams.get()).toString());
        }

    }

    
    
 

    
    @Test
    public void walletCreateEncryptTest() throws Exception {

     
        byte[] salt = "salt".getBytes();
      //  random.nextBytes(salt);

        KeySpec spec = new PBEKeySpec("password".toCharArray(), salt, 65536, 256); // AES-256
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        byte[] key = f.generateSecret(spec).getEncoded();
        
        byte[] a = WalletUtil.createWallet(MainNetParams.get());
        
    
        byte[] b = WalletUtil.encrypt(key, a);
        Wallet wallet = WalletUtil.loadWallet(false, new ByteArrayInputStream(WalletUtil.decrypt(key, b)), MainNetParams.get());

        List<ECKey> issuedKeys = wallet.walletKeys(null);
        assertTrue(issuedKeys.size() == 1);
        for (ECKey ecKey : issuedKeys) {
            log.debug(ecKey.getPublicKeyAsHex());
            log.debug(ecKey.getPrivateKeyAsHex());
            log.debug(ecKey.toAddress(MainNetParams.get()).toString());
        }

    }

    
    @Test
    public void setPassword() throws Exception {

        byte[] a = WalletUtil.createWallet(MainNetParams.get());
        Wallet wallet = WalletUtil.loadWallet(false, new ByteArrayInputStream(a), MainNetParams.get());

        List<ECKey> issuedKeys = wallet.walletKeys(null);
        assertTrue(issuedKeys.size() > 0);
      
        String oldPassword = "test";
        String password = "test";
        KeyCrypterScrypt scrypt = new KeyCrypterScrypt(SCRYPT_PARAMETERS);

        KeyParameter aesKey = scrypt.deriveKey(password);

        // Write the target time to the wallet so we can make the
        // progress bar work when entering the password.

        // The actual encryption part doesn't take very long as most
        // private keys are derived on demand.
        log.info("Key derived, now encrypting");
        if (wallet.isEncrypted()) {
            wallet.decrypt(oldPassword);
        }
       wallet.encrypt(scrypt, aesKey);
       List<ECKey>   k2=wallet.walletKeys(aesKey);
  
        assertTrue(wallet.isEncrypted());
      //  assertEquals(issuedKeys, k2);
        issuedKeys.stream().allMatch(num -> k2.contains(num));
    }

    public static final Protos.ScryptParameters SCRYPT_PARAMETERS = Protos.ScryptParameters.newBuilder().setP(6).setR(8)
            .setN(32768).setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt())).build();
    
    @Test
    // transfer the coin to address
    public void walletSingleKey() throws Exception {

        ECKey from = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(from);
        Wallet wallet = Wallet.fromKeys(MainNetParams.get(), keys);
        assertEquals(wallet.walletKeys().size(), 1) ;
    }
    @Test(expected = ArithmeticException.class)
    public void checkDecimal() throws Exception {

        ECKey from = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(from);
        Wallet wallet = Wallet.fromKeys(MainNetParams.get(), keys);
        
        wallet.totalAmount(1, 1000, 6);
        
    }

}
