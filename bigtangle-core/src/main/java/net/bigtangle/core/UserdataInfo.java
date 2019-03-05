/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.ArrayList;
import java.util.List;

public class UserdataInfo extends DataClass implements java.io.Serializable {

    private static final long serialVersionUID = 1554582498768357964L;

    private Token tokens;
    
    private TokenSerial tokenSerial;
    
    private List<MultiSignAddress> multiSignAddresses;
   
    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
    
    public UserdataInfo parse(byte[] buf) {
        String jsonStr = new String(buf);
        try {
            UserdataInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, UserdataInfo.class);
            if (tokenInfo == null) return this;
            this.tokens = tokenInfo.getTokens();
            this.tokenSerial = tokenInfo.getTokenSerial();
            this.multiSignAddresses.clear();
            this.multiSignAddresses.addAll(tokenInfo.getMultiSignAddresses());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    public Token getTokens() {
        return tokens;
    }

    public void setTokens(Token tokens) {
        this.tokens = tokens;
    }
    
    public TokenSerial getTokenSerial() {
        return tokenSerial;
    }

    public void setTokenSerial(TokenSerial tokenSerial) {
        this.tokenSerial = tokenSerial;
    }

    public List<MultiSignAddress> getMultiSignAddresses() {
        return multiSignAddresses;
    }

    public void setMultiSignAddresses(List<MultiSignAddress> multiSignAddresses) {
        this.multiSignAddresses = multiSignAddresses;
    }

    
    public UserdataInfo() {
        this.multiSignAddresses = new ArrayList<>();
    }
}
