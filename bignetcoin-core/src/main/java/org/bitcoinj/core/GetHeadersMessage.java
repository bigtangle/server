/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.util.List;

/**
 * <p>The "getheaders" command is structurally identical to "getblocks", but has different meaning. On receiving this
 * message a Bitcoin node returns matching blocks up to the limit, but without the bodies. It is useful as an
 * optimization: when your wallet does not contain any keys created before a particular time, you don't have to download
 * the bodies for those blocks because you know there are no relevant transactions.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class GetHeadersMessage extends GetBlocksMessage {
    public GetHeadersMessage(NetworkParameters params, List<Sha256Hash> locator, Sha256Hash stopHash) {
        super(params, locator, stopHash);
    }

    public GetHeadersMessage(NetworkParameters params, byte[] payload) throws ProtocolException {
        super(params, payload);
    }

    @Override
    public String toString() {
        return "getheaders: " + Utils.join(locator);
    }

    /**
     * Compares two getheaders messages. Note that even though they are structurally identical a GetHeadersMessage
     * will not compare equal to a GetBlocksMessage containing the same data.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetHeadersMessage other = (GetHeadersMessage) o;
        return version == other.version && stopHash.equals(other.stopHash) &&
            locator.size() == other.locator.size() && locator.containsAll(other.locator);  // ignores locator ordering
    }

    @Override
    public int hashCode() {
        int hashCode = (int)version ^ "getheaders".hashCode() ^ stopHash.hashCode();
        for (Sha256Hash aLocator : locator) hashCode ^= aLocator.hashCode(); // ignores locator ordering
        return hashCode;
    }
}
