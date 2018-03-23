package com.bignetcoin.server;

import java.io.File;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.junit.Test;

public class KeyBagTest {

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override
        public int getInterval() {
            return 10000;
        }
    };

    @Test
    public void testPubKey() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(PARAMS, new File("."), "bignetcoin");
        for (ECKey realKey : bitcoin.wallet().getImportedKeys()) {
            System.out.println("realKey, pubKey : " + realKey.getPublicKeyAsHex() + ", prvKey : " + realKey.getPrivateKeyAsHex());
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey realKey : chain.getLeafKeys()) {
                System.out.println("realKey, pubKey : " + realKey.getPublicKeyAsHex() + ", priKey : " + realKey.getPrivateKeyAsHex());
            }
        }
    }
}
