/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import javafx.beans.property.SimpleStringProperty;
import net.bigtangle.core.Utils;

public class CoinModel {
    private SimpleStringProperty value;
    private SimpleStringProperty tokenid;

    public SimpleStringProperty value() {
        return value;
    }

    public SimpleStringProperty tokenid() {
        return tokenid;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String value) {
        this.value.set(value);
    }

    public String getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(String tokenid) {
        this.tokenid.set(tokenid);
    }

    public CoinModel(String value, byte[] tokenid) {
        super();
        this.value = new SimpleStringProperty(value);
        this.tokenid = new SimpleStringProperty(Utils.HEX.encode(tokenid));
    }

}
