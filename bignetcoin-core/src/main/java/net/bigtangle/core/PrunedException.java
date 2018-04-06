/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

// TODO: Rename PrunedException to something like RequiredDataWasPrunedException

/**
 * PrunedException is thrown in cases where a fully verifying node has deleted (pruned) old block data that turned
 * out to be necessary for handling a re-org. Normally this should never happen unless you're playing with the testnet
 * as the pruning parameters should be set very conservatively, such that an absolutely enormous re-org would be
 * required to trigger it.
 */
@SuppressWarnings("serial")
public class PrunedException extends Exception {
    private Sha256Hash hash;
    public PrunedException(Sha256Hash hash) {
        super(hash.toString());
        this.hash = hash;
    }
    public Sha256Hash getHash() {
        return hash;
    }
}
