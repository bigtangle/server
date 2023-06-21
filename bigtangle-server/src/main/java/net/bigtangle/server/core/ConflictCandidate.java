/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.core;

import com.google.common.base.Objects;

import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Token;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.server.data.ContractResult;

public class ConflictCandidate {

    private BlockWrap block;
	private ConflictPoint conflictPoint;

    private ConflictCandidate(BlockWrap block, ConflictPoint conflictPoint) {
        super();
        this.block = block;
        this.conflictPoint = conflictPoint;
	}

    public static ConflictCandidate fromTransactionOutpoint(BlockWrap block, TransactionOutPoint connectedOutpoint) {
        return new ConflictCandidate(block, ConflictPoint.fromTransactionOutpoint(connectedOutpoint));
    }

    public static ConflictCandidate fromReward(BlockWrap block, RewardInfo reward) {
        return new ConflictCandidate(block, ConflictPoint.fromReward(reward));
    }

    public static ConflictCandidate fromToken(BlockWrap block, Token token) {
        return new ConflictCandidate(block, ConflictPoint.fromToken(token));
    }

    public static ConflictCandidate fromConflictPoint(BlockWrap block, ConflictPoint conflictPoint) {
        return new ConflictCandidate(block, conflictPoint);
    }

    public static ConflictCandidate fromDomainToken(BlockWrap block, Token token) {
        return new ConflictCandidate(block, ConflictPoint.fromDomainToken(token));
    }
    public static ConflictCandidate fromContractExecute(BlockWrap block, ContractResult token) {
        return new ConflictCandidate(block, ConflictPoint.fromContractExecute(token));
    }

	public BlockWrap getBlock() {
        return block;
    }

    public ConflictPoint getConflictPoint() {
        return conflictPoint;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConflictCandidate other = (ConflictCandidate) o;

        return block.equals(other.block) && conflictPoint.equals(other.conflictPoint);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(block, conflictPoint);
    }
}