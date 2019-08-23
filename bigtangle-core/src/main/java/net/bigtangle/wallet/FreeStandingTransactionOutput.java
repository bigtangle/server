/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.wallet;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;

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
 
    @Override
    public int getIndex() {
        return (int) output.getIndex();
    }

    @Override
    public Sha256Hash getParentTransactionHash() {
        return output.getTxHash();
    }
}
