/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

public class ConflictPoint {
    private ConflictType type;
    
    /** Null if not conflict of corresponding type */
    @Nullable
    private TransactionOutPoint connectedOutpoint;
    @Nullable
    private long connectedRewardHeight;
    @Nullable
    private Token connectedToken;

    public ConflictPoint(TransactionOutPoint connectedOutpoint) {
        super();
        this.type = ConflictType.TXOUT;
        this.connectedOutpoint = connectedOutpoint;
    }

    public ConflictPoint(long fromHeight) {
        super();
        this.type = ConflictType.REWARDISSUANCE;
        this.connectedRewardHeight = fromHeight;
    }

    public ConflictPoint(Token token) {
        super();
        this.type = ConflictType.TOKENISSUANCE;
        this.connectedToken = token;
    }
    
    // TODO Refactor stop using equals (here and in spark too)
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConflictPoint other = (ConflictPoint) o;
        if (other.type != type)
            return false;

        switch (type) {
        case REWARDISSUANCE:
            return getConnectedRewardHeight() == other.getConnectedRewardHeight();
        case TOKENISSUANCE:
            return getConnectedToken().getTokenindex() == other.getConnectedToken().getTokenindex()
                    && getConnectedToken().getTokenid().equals(other.getConnectedToken().getTokenid());
        case TXOUT:
            return getConnectedOutpoint().getIndex() == other.getConnectedOutpoint().getIndex()
                    && getConnectedOutpoint().getHash().equals(other.getConnectedOutpoint().getHash());
        default:
            return true;
        }
    }

    @Override
    public int hashCode() {
        switch (type) {
        case REWARDISSUANCE:
            return Objects.hashCode(getConnectedRewardHeight());
        case TOKENISSUANCE:
            return Objects.hashCode(getConnectedToken().getTokenindex(), getConnectedToken().getTokenid());
        case TXOUT:
            return Objects.hashCode(getConnectedOutpoint().getIndex(), getConnectedOutpoint().getHash());
        default:
            return super.hashCode();
        }
    }

    public enum ConflictType {
        TXOUT, TOKENISSUANCE, REWARDISSUANCE
    }

    public ConflictType getType() {
        return type;
    }

    public TransactionOutPoint getConnectedOutpoint() {
        return connectedOutpoint;
    }

    public long getConnectedRewardHeight() {
        return connectedRewardHeight;
    }

    public Token getConnectedToken() {
        return connectedToken;
    }
}
