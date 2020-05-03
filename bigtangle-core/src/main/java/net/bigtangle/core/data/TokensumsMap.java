package net.bigtangle.core.data;

import java.util.HashMap;
import java.util.Map;

public class TokensumsMap {
    Map<String, Tokensums>  tokensumsMap;

    
    public TokensumsMap( ) {
        tokensumsMap = new HashMap<String, Tokensums> ();
    }
    public TokensumsMap(Map<String, Tokensums> tokensumsMap) {
        super();
        this.tokensumsMap = tokensumsMap;
    }

    public Map<String, Tokensums> getTokensumsMap() {
        return tokensumsMap;
    }

    public void setTokensumsMap(Map<String, Tokensums> tokensumsMap) {
        this.tokensumsMap = tokensumsMap;
    }
    
}
