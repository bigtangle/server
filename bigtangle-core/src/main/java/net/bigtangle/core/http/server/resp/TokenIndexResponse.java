package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.http.AbstractResponse;

public class TokenIndexResponse extends AbstractResponse {

    private int tokenindex;

    private String blockhash;

    public int getTokenindex() {
        return tokenindex;
    }

    public String getBlockhash() {
        return blockhash;
    }

    public static AbstractResponse createTokenSerialIndexResponse(int tokenindex, String blockhash) {
        TokenIndexResponse res = new TokenIndexResponse();
        res.tokenindex = tokenindex;
        res.blockhash = blockhash;
        return res;
    }
}
