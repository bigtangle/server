/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 *
 */
public class OrderCancel extends SpentBlock {

    // this is the block hash of the Order Block, which should be canceled
    private Sha256Hash orderBlockHash;

    // JSON
    public OrderCancel() {
    }

    public Sha256Hash getOrderBlockHash() {
        return orderBlockHash;
    }

    public void setOrderBlockHash(Sha256Hash orderBlockHash) {
        this.orderBlockHash = orderBlockHash;
    }

    public OrderCancel(Sha256Hash orderBlockHash) {
        super();
        setDefault(); 
        this.orderBlockHash = orderBlockHash;
    }

}
