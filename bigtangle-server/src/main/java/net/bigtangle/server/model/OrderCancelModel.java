/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.Sha256Hash;

/**
 *
 */
public class OrderCancelModel extends SpentBlockModel {

    // this is the block hash of the Order Block, which should be canceled
    private String orderblockhash;

    public String getOrderblockhash() {
        return orderblockhash;
    }

    public void setOrderblockhash(String orderblockhash) {
        this.orderblockhash = orderblockhash;
    }

    public static OrderCancelModel from(OrderCancel orderCancel) {
        OrderCancelModel p = new OrderCancelModel();
        p.setOrderblockhash(orderCancel.getOrderBlockHash().toString());
        p.fromSpentBlock(orderCancel);
        return p;
    }

    public OrderCancel toOrderCancel() {
        OrderCancel p = new OrderCancel();
        toSpentBlock(p);
        p.setOrderBlockHash(Sha256Hash.wrap(getOrderblockhash()));
        return null;
    }

}
