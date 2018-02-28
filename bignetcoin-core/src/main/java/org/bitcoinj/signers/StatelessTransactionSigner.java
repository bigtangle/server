/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.signers;

/**
 * A signer that doesn't have any state to be serialized.
 */
public abstract class StatelessTransactionSigner implements TransactionSigner {
    @Override
    public void deserialize(byte[] data) {
    }

    @Override
    public byte[] serialize() {
        return new byte[0];
    }
}
