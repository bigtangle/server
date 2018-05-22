package net.bigtangle.server.response;

public class TokenSerialIndexResponse extends AbstractResponse {

    private int tokenindex;

    public int getTokenindex() {
        return tokenindex;
    }

    public void setTokenindex(int tokenindex) {
        this.tokenindex = tokenindex;
    }

    public static AbstractResponse createTokenSerialIndexResponse(int tokenindex) {
        TokenSerialIndexResponse res = new TokenSerialIndexResponse();
        res.tokenindex = tokenindex;
        return res;
    }
}
