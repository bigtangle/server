/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.testing;

import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.KeyBag;

public class NopTransactionSigner implements TransactionSigner {
    private boolean isReady;

    public NopTransactionSigner() {
    }

    public NopTransactionSigner(boolean ready) {
        this.isReady = ready;
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public byte[] serialize() {
        return isReady ? new byte[]{1} : new byte[]{0};
    }

    @Override
    public void deserialize(byte[] data) {
        if (data.length > 0)
            isReady = data[0] == 1;
    }

    @Override
    public boolean signInputs(ProposedTransaction t, KeyBag keyBag) {
        return false;
    }
}
