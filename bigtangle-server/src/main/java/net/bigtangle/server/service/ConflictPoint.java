/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.TransactionOutPoint;

public class ConflictPoint {

    private BlockWrap block;
    private ConflictType type;
    
    /** Null if not conflict of corresponding type */
    @Nullable
    private TransactionOutPoint connectedOutpoint;
    @Nullable
    private long connectedRewardHeight;
    @Nullable
    private TokenSerial connectedTokenSerial;

    public ConflictPoint(BlockWrap block, TransactionOutPoint connectedOutpoint) {
        super();
        this.block = block;
        this.type = ConflictType.TXOUT;
        this.connectedOutpoint = connectedOutpoint;
    }

    public ConflictPoint(BlockWrap block, long fromHeight) {
        super();
        this.block = block;
        this.type = ConflictType.REWARDISSUANCE;
        this.connectedRewardHeight = fromHeight;
    }

    public ConflictPoint(BlockWrap block, TokenSerial serial) {
        super();
        this.block = block;
        this.type = ConflictType.TOKENISSUANCE;
        this.connectedTokenSerial = serial;
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
            return getConnectedRewardHeight() == other.getConnectedRewardHeight();
        case TOKENISSUANCE:
            return getConnectedTokenSerial().getTokenindex() == other.getConnectedTokenSerial().getTokenindex()
                    && getConnectedTokenSerial().getTokenid().equals(other.getConnectedTokenSerial().getTokenid());
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
            return Objects.hashCode(getConnectedTokenSerial().getTokenindex(), getConnectedTokenSerial().getTokenid());
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

    public TokenSerial getConnectedTokenSerial() {
        return connectedTokenSerial;
    }

    public BlockWrap getBlock() {
        return block;
    }
}
