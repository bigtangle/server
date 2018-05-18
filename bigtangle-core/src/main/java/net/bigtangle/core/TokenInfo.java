package net.bigtangle.core;

import java.util.ArrayList;
import java.util.List;

public class TokenInfo implements java.io.Serializable {

    private static final long serialVersionUID = 1554582498768357964L;

    private Tokens tokens;
    
    private List<TokenSerial> tokenSerials;
    
    private List<MultiSignAddress> multiSignAddresses;
   

    public Tokens getTokens() {
        return tokens;
    }

    public void setTokens(Tokens tokens) {
        this.tokens = tokens;
    }

    public List<TokenSerial> getTokenSerials() {
        return tokenSerials;
    }

    public void setTokenSerials(List<TokenSerial> tokenSerials) {
        this.tokenSerials = tokenSerials;
    }

    public List<MultiSignAddress> getMultiSignAddresses() {
        return multiSignAddresses;
    }

    public void setMultiSignAddresses(List<MultiSignAddress> multiSignAddresses) {
        this.multiSignAddresses = multiSignAddresses;
    }

    
    public TokenInfo() {
        this.tokenSerials = new ArrayList<>();
        this.multiSignAddresses = new ArrayList<>();
 
    }
}
