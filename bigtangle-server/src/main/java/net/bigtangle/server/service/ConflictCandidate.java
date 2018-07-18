package net.bigtangle.server.service;

import com.google.common.base.Objects;

import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.TransactionOutPoint;

public class ConflictCandidate {

    private BlockWrap block;
	private ConflictPoint conflictPoint;

    public ConflictCandidate(BlockWrap block, TransactionOutPoint connectedOutpoint) {
        super();
        this.block = block;
        this.conflictPoint = new ConflictPoint(connectedOutpoint);
    }

    public ConflictCandidate(BlockWrap block, long fromHeight) {
        super();
        this.block = block;
        this.conflictPoint = new ConflictPoint(fromHeight);
    }

    public ConflictCandidate(BlockWrap block, TokenSerial serial) {
        super();
        this.block = block;
        this.conflictPoint = new ConflictPoint(serial);
    }

    public ConflictCandidate(BlockWrap block, ConflictPoint conflictPoint) {
        super();
        this.block = block;
        this.conflictPoint = conflictPoint;
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