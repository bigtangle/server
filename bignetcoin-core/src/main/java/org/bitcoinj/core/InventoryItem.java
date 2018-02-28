/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import com.google.common.base.Objects;

public class InventoryItem {
    
    /**
     * 4 byte uint32 type field + 32 byte hash
     */
    static final int MESSAGE_LENGTH = 36;
    
    public enum Type {
        Error,
        Transaction,
        Block,
        FilteredBlock
    }

    public final Type type;
    public final Sha256Hash hash;

    public InventoryItem(Type type, Sha256Hash hash) {
        this.type = type;
        this.hash = hash;
    }

    @Override
    public String toString() {
        return type + ": " + hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryItem other = (InventoryItem) o;
        return type == other.type && hash.equals(other.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, hash);
    }
}
