/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.core.TransactionOutPoint;

public class ConflictPoint {

    public ConflictType type;

    /** Null if not outpoint conflict point */
    @Nullable
    public TransactionOutPoint connectedOutpoint;

    public ConflictPoint(TransactionOutPoint connectedOutpoint) {
        super();
        this.type = ConflictType.TXOUT;
        this.connectedOutpoint = connectedOutpoint;
    }
    
    // TODO constructors for reward issuance and token issuance

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConflictPoint other = (ConflictPoint) o;
        switch (type) {
        case REWARDISSUANCE:
            return false; // TODO
        case TOKENISSUANCE:
            return false; // TODO
        case TXOUT:
            return connectedOutpoint.getIndex() == other.connectedOutpoint.getIndex()
            && connectedOutpoint.getHash().equals(other.connectedOutpoint.getHash());
        default:
            return false;
        }
    }

    @Override
    public int hashCode() {
        // TODO
        return Objects.hashCode(connectedOutpoint.getIndex(), connectedOutpoint.getHash());
    }

    public enum ConflictType {
        TXOUT, TOKENISSUANCE, REWARDISSUANCE
    }
}
