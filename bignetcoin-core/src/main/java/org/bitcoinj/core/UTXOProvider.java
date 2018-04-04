/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.util.List;

/**
 * A UTXOProvider encapsulates functionality for returning unspent transaction outputs,
 * for use by the wallet or other code that crafts spends.
 *
 * <p>A {@link org.bitcoinj.store.FullPrunedBlockStore} is an internal implementation within bitcoinj.</p>
 */
public interface UTXOProvider {

    // TODO currently the access to outputs is by address. Change to ECKey
    /**
     * Get the list of {@link UTXO}'s for a given address.
     * @param addresses List of address.
     * @return The list of transaction outputs.
     * @throws UTXOProviderException If there is an error.
     */
    List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException;
    
    List<UTXO> getOpenTransactionOutputs(List<Address> addresses, byte[] tokenid) throws UTXOProviderException;
    /**
     * Get the height of the chain head.
     * @return The chain head height.
     * @throws UTXOProvider If there is an error.
     */
    int getChainHeadHeight() throws UTXOProviderException;

    /**
     * The {@link NetworkParameters} of this provider.
     * @return The network parameters.
     */
    NetworkParameters getParams();
}
