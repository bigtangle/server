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
    private RewardInfo connectedReward;
    @Nullable
    private Token connectedToken;
    @Nullable
    private Token connectedDomainToken;

    private ConflictPoint(ConflictType type, TransactionOutPoint connectedOutpoint, RewardInfo reward,
            Token connectedToken, Token connectedDomainToken) {
        super();
        this.type = type;
        this.connectedOutpoint = connectedOutpoint;
        this.connectedReward = reward;
        this.connectedToken = connectedToken;
        this.connectedDomainToken = connectedDomainToken;
    }

    public static ConflictPoint fromTransactionOutpoint(TransactionOutPoint connectedOutpoint) {
        return new ConflictPoint(ConflictType.TXOUT, connectedOutpoint, null, null, null);
    }

    public static ConflictPoint fromReward(RewardInfo reward) {
        return new ConflictPoint(ConflictType.REWARDISSUANCE, null, reward, null, null);
    }

    public static ConflictPoint fromToken(Token token) {
        return new ConflictPoint(ConflictType.TOKENISSUANCE, null, null, token, null);
    }

    public static ConflictPoint fromDomainToken(Token token) {
        return new ConflictPoint(ConflictType.DOMAINISSUANCE, null, null, null, token);
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
            return getConnectedReward().getPrevRewardHash().equals(other.getConnectedReward().getPrevRewardHash());
        case TOKENISSUANCE:
                return getConnectedToken().getTokenid().equals(other.getConnectedToken().getTokenid())
                        && getConnectedToken().getTokenindex() == other.getConnectedToken().getTokenindex();
        case TXOUT:
            return getConnectedOutpoint().getIndex() == other.getConnectedOutpoint().getIndex()
                    && getConnectedOutpoint().getHash().equals(other.getConnectedOutpoint().getHash());
		case DOMAINISSUANCE:
            return getConnectedDomainToken().getDomainNameBlockHash().equals(other.getConnectedDomainToken().getDomainNameBlockHash())
                    && getConnectedDomainToken().getTokenname().equals(other.getConnectedDomainToken().getTokenname());
		default:
			throw new RuntimeException("Conflicts not implemented.");
        }
    }

    @Override
    public int hashCode() {
        switch (type) {
        case REWARDISSUANCE:
            return Objects.hashCode(type, getConnectedReward().getPrevRewardHash());
        case TOKENISSUANCE:
            return Objects.hashCode(type, getConnectedToken().getTokenid(), getConnectedToken().getTokenindex());
        case TXOUT:
            return Objects.hashCode(type, getConnectedOutpoint().getIndex(), getConnectedOutpoint().getHash());
        case DOMAINISSUANCE:
            return Objects.hashCode(type, getConnectedDomainToken().getDomainNameBlockHash(), getConnectedDomainToken().getTokenname());
		default:
			throw new RuntimeException("Conflicts not implemented.");
        }
    }

    public enum ConflictType {
        TXOUT, TOKENISSUANCE, REWARDISSUANCE, DOMAINISSUANCE
    }

    public ConflictType getType() {
        return type;
    }

    public TransactionOutPoint getConnectedOutpoint() {
        return connectedOutpoint;
    }

    public RewardInfo getConnectedReward() {
        return connectedReward;
    }

    public Token getConnectedToken() {
        return connectedToken;
    }

    public Token getConnectedDomainToken() {
        return connectedDomainToken;
    }
}
