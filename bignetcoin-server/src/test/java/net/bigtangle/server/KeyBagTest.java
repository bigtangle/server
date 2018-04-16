/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.wallet.DecryptingKeyBag;
import net.bigtangle.wallet.DeterministicKeyChain;

public class KeyBagTest {

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override
        public int getInterval() {
            return 10000;
        }
    };

    @Test
    public void testPubKey() throws Exception {
        KeyParameter aesKey = null;
        WalletAppKit bitcoin = new WalletAppKit(PARAMS, new File("../bignetcoin"), "bignetcoin");
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), aesKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            System.out.println("realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", prvKey : " + ecKey.getPrivateKeyAsHex());
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                System.out.println("realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", priKey : " + ecKey.getPrivateKeyAsHex());
            }
        }
        
        
        
    }
}
