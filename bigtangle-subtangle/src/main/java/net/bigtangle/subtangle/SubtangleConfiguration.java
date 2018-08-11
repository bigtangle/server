package net.bigtangle.subtangle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SubtangleConfiguration {

    @Value("${subtangle.active:false}")
    private boolean active;

    @Value("${subtangle.pubKeyHex0}")
    private String pubKeyHex0;

    @Value("${subtangle.priKeyHex0}")
    private String priKeyHex0;
    
    @Value("${subtangle.pubKeyHex1}")
    private String pubKeyHex1;

    @Value("${subtangle.priKeyHex1}")
    private String priKeyHex1;

    @Value("${subtangle.parentContextRoot}")
    private String parentContextRoot;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getParentContextRoot() {
        return parentContextRoot;
    }

    public void setParentContextRoot(String parentContextRoot) {
        this.parentContextRoot = parentContextRoot;
    }

    public String getPubKeyHex0() {
        return pubKeyHex0;
    }

    public void setPubKeyHex0(String pubKeyHex0) {
        this.pubKeyHex0 = pubKeyHex0;
    }

    public String getPriKeyHex0() {
        return priKeyHex0;
    }

    public void setPriKeyHex0(String priKeyHex0) {
        this.priKeyHex0 = priKeyHex0;
    }

    public String getPubKeyHex1() {
        return pubKeyHex1;
    }

    public void setPubKeyHex1(String pubKeyHex1) {
        this.pubKeyHex1 = pubKeyHex1;
    }

    public String getPriKeyHex1() {
        return priKeyHex1;
    }

    public void setPriKeyHex1(String priKeyHex1) {
        this.priKeyHex1 = priKeyHex1;
    }
}
