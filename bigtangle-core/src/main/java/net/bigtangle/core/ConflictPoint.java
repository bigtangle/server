/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import javax.annotation.Nullable;

import org.apache.commons.lang3.NotImplementedException;

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
    private OrderReclaimInfo connectedOrder;
    @Nullable
    private Token connectedDomainToken;

    private ConflictPoint(ConflictType type, TransactionOutPoint connectedOutpoint, RewardInfo reward,
            Token connectedToken, OrderReclaimInfo connectedOrder, Token connectedDomainToken) {
        super();
        this.type = type;
        this.connectedOutpoint = connectedOutpoint;
        this.connectedReward = reward;
        this.connectedToken = connectedToken;
        this.connectedOrder = connectedOrder;
        this.connectedDomainToken = connectedDomainToken;
    }

    public static ConflictPoint fromTransactionOutpoint(TransactionOutPoint connectedOutpoint) {
        return new ConflictPoint(ConflictType.TXOUT, connectedOutpoint, null, null, null, null);
    }

    public static ConflictPoint fromReward(RewardInfo reward) {
        return new ConflictPoint(ConflictType.REWARDISSUANCE, null, reward, null, null, null);
    }

    public static ConflictPoint fromToken(Token token) {
        return new ConflictPoint(ConflictType.TOKENISSUANCE, null, null, token, null, null);
    }

    public static ConflictPoint fromOrder(OrderReclaimInfo connectedOrder) {
        return new ConflictPoint(ConflictType.ORDERRECLAIM, null, null, null, connectedOrder, null);
    }

    public static ConflictPoint fromDomainToken(Token token) {
        return new ConflictPoint(ConflictType.DOMAINISSUANCE, null, null, null, null, token);
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
            return (getConnectedToken().getTokenid().equals(other.getConnectedToken().getTokenid())
                    && getConnectedToken().getTokenindex() == other.getConnectedToken().getTokenindex())
                    || (getConnectedToken().getDomainPredecessorBlockHash()
                            .equals(other.getConnectedToken().getDomainPredecessorBlockHash())
                            && getConnectedToken().getTokenname().equals(other.getConnectedToken().getTokenname()));
        case TXOUT:
            return getConnectedOutpoint().getIndex() == other.getConnectedOutpoint().getIndex()
                    && getConnectedOutpoint().getHash().equals(other.getConnectedOutpoint().getHash());
        case ORDERRECLAIM:
            return getConnectedOrder().getOrderBlockHash().equals(other.getConnectedOrder().getOrderBlockHash());
        case DOMAINISSUANCE:
            return getConnectedDomainToken().getDomainPredecessorBlockHash()
                    .equals(other.getConnectedDomainToken().getDomainPredecessorBlockHash())
                    && getConnectedDomainToken().getTokenname().equals(other.getConnectedDomainToken().getTokenname());
        default:
            throw new NotImplementedException("Conflicts not implemented.");
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
        case ORDERRECLAIM:
            return Objects.hashCode(type, getConnectedOrder().getOrderBlockHash());
        case DOMAINISSUANCE:
            return Objects.hashCode(type, getConnectedDomainToken().getDomainPredecessorBlockHash(),
                    getConnectedDomainToken().getDomainName());
        default:
            throw new NotImplementedException("Conflicts not implemented.");
        }
    }

    public enum ConflictType {
        TXOUT, TOKENISSUANCE, REWARDISSUANCE, ORDERRECLAIM, DOMAINISSUANCE
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

    public OrderReclaimInfo getConnectedOrder() {
        return connectedOrder;
    }

    public Token getConnectedDomainToken() {
        return connectedDomainToken;
    }
}
