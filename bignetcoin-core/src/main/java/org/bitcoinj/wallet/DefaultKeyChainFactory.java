/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet;

import org.bitcoinj.crypto.*;

/**
 * Default factory for creating keychains while de-serializing.
 */
public class DefaultKeyChainFactory implements KeyChainFactory {
    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried) {
        DeterministicKeyChain chain;
        if (isMarried)
            chain = new MarriedKeyChain(seed, crypter);
        else
            chain = new DeterministicKeyChain(seed, crypter);
        return chain;
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey,
                                                      boolean isFollowingKey, boolean isMarried) throws UnreadableWalletException {
        if (!accountKey.getPath().equals(DeterministicKeyChain.ACCOUNT_ZERO_PATH))
            throw new UnreadableWalletException("Expecting account key but found key with path: " +
                    HDUtils.formatPath(accountKey.getPath()));
        DeterministicKeyChain chain;
        if (isMarried)
            chain = new MarriedKeyChain(accountKey);
        else
            chain = new DeterministicKeyChain(accountKey, isFollowingKey);
        return chain;
    }
}
