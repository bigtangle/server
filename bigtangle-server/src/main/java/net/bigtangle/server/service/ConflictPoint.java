/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.TransactionOutPoint;

public class ConflictPoint {

    private ConflictType type;
    /** Null if not conflict of corresponding type */
    @Nullable
    private TransactionOutPoint connectedOutpoint;
    @Nullable
    private long fromHeight;
    @Nullable
    private TokenSerial connectedTokenSerial;

    public ConflictPoint(TransactionOutPoint connectedOutpoint) {
        super();
        this.type = ConflictType.TXOUT;
        this.connectedOutpoint = connectedOutpoint;
    }

    public ConflictPoint(long fromHeight) {
        super();
        this.type = ConflictType.REWARDISSUANCE;
        this.fromHeight = fromHeight;
    }

    public ConflictPoint(TokenSerial serial) {
        super();
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
            return getHeight() == other.getHeight();
        case TOKENISSUANCE:
            return getTokenSerial().getTokenindex() == other.getTokenSerial().getTokenindex()
                    && getTokenSerial().getTokenid().equals(other.getTokenSerial().getTokenid());
        case TXOUT:
            return getOutpoint().getIndex() == other.getOutpoint().getIndex()
                    && getOutpoint().getHash().equals(other.getOutpoint().getHash());
        default:
            return true;
        }
    }

    @Override
    public int hashCode() {
        switch (type) {
        case REWARDISSUANCE:
            return Objects.hashCode(getHeight());
        case TOKENISSUANCE:
            return Objects.hashCode(getTokenSerial().getTokenindex(), getTokenSerial().getTokenid());
        case TXOUT:
            return Objects.hashCode(getOutpoint().getIndex(), getOutpoint().getHash());
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

    public TransactionOutPoint getOutpoint() {
        return connectedOutpoint;
    }

    public long getHeight() {
        return fromHeight;
    }

    public TokenSerial getTokenSerial() {
        return connectedTokenSerial;
    }
}
