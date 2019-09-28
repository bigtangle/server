/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.http.AbstractResponse;

public class TokenIndexResponse extends AbstractResponse {

    private long tokenindex;

    private Sha256Hash blockhash;

    public long getTokenindex() {
        return tokenindex;
    }

    public Sha256Hash getBlockhash() {
        return blockhash;
    }

    public static AbstractResponse createTokenSerialIndexResponse(long tokenindex, Sha256Hash blockhash) {
        TokenIndexResponse res = new TokenIndexResponse();
        res.tokenindex = tokenindex;
        res.blockhash = blockhash;
        return res;
    }
}
