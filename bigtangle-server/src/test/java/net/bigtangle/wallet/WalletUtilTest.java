/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.google.protobuf.ByteString;

import net.bigtangle.core.ECKey;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletUtil;
import net.bigtangle.params.MainNetParams;

public class WalletUtilTest {

    private static final Logger log = LoggerFactory.getLogger(WalletUtilTest.class);

    @Test
    public void walletCreateLoadTest() throws Exception {

        byte[] a = WalletUtil.createWallet(MainNetParams.get());
        Wallet wallet = WalletUtil.loadWallet(false, new ByteArrayInputStream(a), MainNetParams.get());

        List<ECKey> issuedKeys = wallet.walletKeys(null);
        assertTrue(issuedKeys.size() > 0);
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

}
