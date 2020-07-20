/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.bigtangle.utils.Json;

public class UserdataInfo extends DataClass implements java.io.Serializable {

    private static final long serialVersionUID = 1554582498768357964L;

    private Token tokens;
    
  
    
    private List<MultiSignAddress> multiSignAddresses;
   
    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    
    }
    
    public UserdataInfo parse(byte[] buf) {
        String jsonStr = new String(buf);
        try {
            UserdataInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, UserdataInfo.class);
            if (tokenInfo == null) return this;
            this.tokens = tokenInfo.getTokens();
     
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
