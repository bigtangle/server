/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.transaction;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;

public class FreeStandingTransactionOutput extends TransactionOutput {
    
    private UTXO output;
    private int chainHeight;

    /**
     * Construct a free standing Transaction Output.
     * 
     * @param params
     *            The network parameters.
     * @param output
     *            The stored output (free standing).
     */
    public FreeStandingTransactionOutput(NetworkParameters params, UTXO output, int chainHeight) {
        super(params, null, output.getValue(), output.getScript().getProgram());
        this.output = output;
        this.chainHeight = chainHeight;
    }

    /**
     * Get the {@link UTXO}.
     * 
     * @return The stored output.
     */
    public UTXO getUTXO() {
        return output;
    }

    /**
     * Get the depth withing the chain of the parent tx, depth is 1 if it
     * the output height is the height of the latest block.
     * 
     * @return The depth.
     */
    @Override
    public long getParentTransactionDepthInBlocks() {
        return chainHeight - output.getHeight() + 1;
    }

    @Override
    public int getIndex() {
        return (int) output.getIndex();
    }

    @Override
    public Sha256Hash getParentTransactionHash() {
        return output.getHash();
    }
}
