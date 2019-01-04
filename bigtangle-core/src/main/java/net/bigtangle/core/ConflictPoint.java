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
    private String connectedPrevRewardHash;
    @Nullable
    private Token connectedToken;

    private ConflictPoint(ConflictType type, TransactionOutPoint connectedOutpoint, String connectedPrevReward,
            Token connectedPrevToken) {
        super();
        this.type = type;
        this.connectedOutpoint = connectedOutpoint;
        this.connectedPrevRewardHash = connectedPrevReward;
        this.connectedToken = connectedPrevToken;
    }

    public static ConflictPoint fromTransactionOutpoint(TransactionOutPoint connectedOutpoint) {
        return new ConflictPoint(ConflictType.TXOUT, connectedOutpoint, null, null);
    }

    public static ConflictPoint fromRewardBlockHash(String prevRewardHash) {
        return new ConflictPoint(ConflictType.REWARDISSUANCE, null, prevRewardHash, null);
    }

    public static ConflictPoint fromToken(Token token) {
        return new ConflictPoint(ConflictType.TOKENISSUANCE, null, null, token);
    }
    
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
            return getConnectedPrevReward().equals(other.getConnectedPrevReward());
        case TOKENISSUANCE:
            // Dynamic conflicts: token issuances with index>0 require the previous issuance, while index=0 uses the tokenid as conflict point
            if (getConnectedToken().getTokenindex() != other.getConnectedToken().getTokenindex())
                return false;
            else if (getConnectedToken().getTokenindex() != 0)
                return getConnectedToken().getPrevblockhash().equals(other.getConnectedToken().getPrevblockhash());
            else 
                return getConnectedToken().getTokenid().equals(other.getConnectedToken().getTokenid());
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
            return Objects.hashCode(getConnectedPrevReward());
        case TOKENISSUANCE:
            return Objects.hashCode(getConnectedToken().getPrevblockhash());
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

    public String getConnectedPrevReward() {
        return connectedPrevRewardHash;
    }

    public Token getConnectedToken() {
        return connectedToken;
    }
}
