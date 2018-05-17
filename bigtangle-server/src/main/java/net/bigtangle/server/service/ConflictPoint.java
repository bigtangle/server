/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import com.google.common.base.Objects;

import net.bigtangle.core.TransactionOutPoint;

public class ConflictPoint {
        // The connected outpoint.
        public TransactionOutPoint connectedOutpoint;

        public ConflictPoint(TransactionOutPoint connectedOutpoint) {
            super();
            this.connectedOutpoint = connectedOutpoint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConflictPoint other = (ConflictPoint) o;
            return connectedOutpoint.getIndex() == other.connectedOutpoint.getIndex() && connectedOutpoint.getHash().equals(other.connectedOutpoint.getHash());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(connectedOutpoint.getIndex(), connectedOutpoint.getHash());
        }
}
