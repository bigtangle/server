/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.http.AbstractResponse;

public class TokenIndexResponse extends AbstractResponse {

    private long tokenindex;

    private String blockhash;

    public long getTokenindex() {
        return tokenindex;
    }

    public String getBlockhash() {
        return blockhash;
    }

    public static AbstractResponse createTokenSerialIndexResponse(long tokenindex, String blockhash) {
        TokenIndexResponse res = new TokenIndexResponse();
        res.tokenindex = tokenindex;
        res.blockhash = blockhash;
        return res;
    }
}
