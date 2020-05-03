/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.response;

import net.bigtangle.core.data.TokensumsMap;

public class CheckpointResponse extends AbstractResponse {

    private TokensumsMap  tokensumsMap;
 
    public static CheckpointResponse create(TokensumsMap  tokensumsMap) {
        CheckpointResponse res = new CheckpointResponse();
        res.tokensumsMap =  tokensumsMap;
        return res;
    }

    public TokensumsMap getTokensumsMap() {
        return tokensumsMap;
    }

    public void setTokensumsMap(TokensumsMap tokensumsMap) {
        this.tokensumsMap = tokensumsMap;
    }

    
     
}
