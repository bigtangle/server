/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import javafx.beans.property.SimpleStringProperty;

public class EckeyModel {
    private SimpleStringProperty pubkeyHex;
    private SimpleStringProperty privkeyHex;
    private SimpleStringProperty addressHex;

    public EckeyModel(String pubkeyHex, String privkeyHex, String addressHex) {
        this.privkeyHex = new SimpleStringProperty(privkeyHex);
        this.pubkeyHex = new SimpleStringProperty(pubkeyHex);
        this.addressHex = new SimpleStringProperty(addressHex);
    }

    public SimpleStringProperty pubkeyHex() {
        return pubkeyHex;

    }

    public SimpleStringProperty privkeyHex() {
        return privkeyHex;

    }

    public SimpleStringProperty addressHex() {
        return addressHex;

    }

    public String getPubkeyHex() {
        return pubkeyHex.get();
    }

    public void setPubkeyHex(String pubkeyHex) {
        this.pubkeyHex.set(pubkeyHex);
    }

    public String getAddressHex() {
        return addressHex.get();
    }

    public void setAddressHex(String addressHex) {
        this.addressHex.set(addressHex);
    }

    public String getPrivkeyHex() {
        return privkeyHex.get();
    }

    public void setPrivkeyHex(String privkeyHex) {
        this.privkeyHex.set(privkeyHex);
    }

}
