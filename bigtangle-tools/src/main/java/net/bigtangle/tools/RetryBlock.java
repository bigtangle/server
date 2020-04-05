package net.bigtangle.tools;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;

public class RetryBlock {
    public static NetworkParameters networkParameters = MainNetParams.get();

    public static void main(String[] args) throws  Exception{
        Wallet wallet= new Wallet(networkParameters);
        String url = "https://61.181.128.230:8088/";
        wallet.setServerURL(url);
        wallet.retryBlock("00002b3d3cf5c2b048dad06faec7195cb7b6dd5ad311c42d1a2f8e5bcefa35bd");
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
