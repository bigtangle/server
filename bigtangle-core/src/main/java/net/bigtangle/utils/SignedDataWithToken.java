package net.bigtangle.utils;

import net.bigtangle.apps.data.SignedData;
import net.bigtangle.core.Token;

public class SignedDataWithToken {
    private SignedData signedData;
    private Token token;
    
    
    public SignedDataWithToken(SignedData signedData, Token token) {
        super();
        this.signedData = signedData;
        this.token = token;
    }
    public SignedData getSignedData() {
        return signedData;
    }
    public void setSignedData(SignedData signedData) {
        this.signedData = signedData;
    }
    public Token getToken() {
        return token;
    }
    public void setToken(Token token) {
        this.token = token;
    }
 
}
