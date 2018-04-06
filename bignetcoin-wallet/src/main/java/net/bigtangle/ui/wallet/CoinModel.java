/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import net.bigtangle.core.Utils;

public class CoinModel {
    private SimpleLongProperty value;
    private SimpleStringProperty tokenid;

    public SimpleLongProperty value() {
        return value;
    }

    public SimpleStringProperty tokenid() {
        return tokenid;
    }

    public long getValue() {
        return value.get();
    }

    public void setValue(long value) {
        this.value.set(value);
    }

    public String getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(String tokenid) {
        this.tokenid.set(tokenid);
    }

    public CoinModel(long value, byte[] tokenid) {
        super();
        this.value = new SimpleLongProperty(value);
        this.tokenid = new SimpleStringProperty(Utils.HEX.encode(tokenid));
    }

}
