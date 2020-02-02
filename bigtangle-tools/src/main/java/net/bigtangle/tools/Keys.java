package net.bigtangle.tools;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.TestParams;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;

public class Keys {
    public static NetworkParameters networkParameters = TestParams.get();

    public static void main(String[] args) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, UnreadableWalletException {
        
        WalletAppKit walletAppKit1 = new WalletAppKit(networkParameters, new File("/home/cui/walletkeys"), "201707040100000004");

        List<ECKey> issuedKeys =  walletAppKit1.wallet().walletKeys(getAesKey(walletAppKit1.wallet(), args[0]));
       for (ECKey k:issuedKeys) {
           System.out.println("public " +k.getPublicKeyAsHex());
           System.out.println("private " +k.getPrivateKeyAsHex());
       }
    }
    public static KeyParameter getAesKey(Wallet wallet, String aespwd)
            throws IOException, UnreadableWalletException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
      
        KeyParameter aesKey = null;
        final KeyCrypterScrypt keyCrypter = (KeyCrypterScrypt) wallet.getKeyCrypter();
 
        if (aespwd != null && wallet.isEncrypted()) {
            aesKey = keyCrypter.deriveKey(aespwd.toString());
        }
        return aesKey;
    }
}
