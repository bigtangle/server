package net.bigtangle.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class TokenInfo implements java.io.Serializable {

    private static final long serialVersionUID = 1554582498768357964L;

    private Token tokens;
    private List<Token> positveTokenList = new ArrayList<Token>(); //TODO remove

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

    public TokenInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        TokenInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, TokenInfo.class);
        if (tokenInfo == null)
            return this;
        this.tokens = tokenInfo.getTokens();
        this.positveTokenList = tokenInfo.getPositveTokenList();
        this.multiSignAddresses = tokenInfo.getMultiSignAddresses();
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

    public TokenInfo() {
        this.multiSignAddresses = new ArrayList<>();
    }

    public List<Token> getPositveTokenList() {
        return positveTokenList;
    }

    public void setPositveTokenList(List<Token> positveTokenList) {
        this.positveTokenList = positveTokenList;
    }
}
