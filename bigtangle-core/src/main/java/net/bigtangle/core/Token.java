/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

public class Token implements java.io.Serializable {
    private static final long serialVersionUID = 6992138619113601243L;

    public Token(String tokenid, String tokenname) {
        this.tokenid = tokenid;
        this.tokenname = tokenname;
    }

    public Token() {
    }
    
    private boolean confirmed;
    private String tokenid;
    private long tokenindex;
    private String tokenname;
    private String description;
    private String url;
    private int signnumber;
    private boolean multiserial; // TODO multiserial is useless?
    private int tokentype; // TODO type is useless? TODO write logic for type differentiation?
    private boolean tokenstop;
    private String prevblockhash;
    
    // not serialized on the wire
    private String blockhash;  // TODO slated for extraction
    private long amount; // TODO  must be inferred on insertion, slated for extraction

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public long getTokenindex() {
        return tokenindex;
    }

    public void setTokenindex(long tokenindex) {
        this.tokenindex = tokenindex;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getTokenname() {
        return tokenname;
    }

    public void setTokenname(String tokenname) {
        this.tokenname = tokenname;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getSignnumber() {
        return signnumber;
    }

    public void setSignnumber(int signnumber) {
        this.signnumber = signnumber;
    }

    public boolean isMultiserial() {
        return multiserial;
    }

    public void setMultiserial(boolean multiserial) {
        this.multiserial = multiserial;
    }

    public int getTokentype() {
        return tokentype;
    }

    public void setTokentype(int tokentype) {
        this.tokentype = tokentype;
    }

    public boolean isTokenstop() {
        return tokenstop;
    }

    public void setTokenstop(boolean tokenstop) {
        this.tokenstop = tokenstop;
    }

    public String getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(String prevblockhash) {
        this.prevblockhash = prevblockhash;
    }

    public static Token buildSimpleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, 
            int signnumber, long tokenindex, long amount, boolean multiserial, boolean tokenstop) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.tokenstop = tokenstop;
        tokens.multiserial = multiserial;
        tokens.tokentype = TokenType.token.ordinal();
        tokens.signnumber = signnumber;
        tokens.amount = amount;
        tokens.tokenindex = tokenindex;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        return tokens;
    }

    public static Token buildMarketTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, String url) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setUrl(url);
        tokens.tokenstop = true;
        tokens.multiserial = false;
        tokens.tokentype = TokenType.market.ordinal();
        tokens.signnumber = 1;
        tokens.amount = 0;
        tokens.tokenindex = 0;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        return tokens;
    }

    public static Token buildSubtangleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, String url) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setUrl(url);
        tokens.tokenstop = true;
        tokens.multiserial = false;
        tokens.tokentype = TokenType.subtangle.ordinal();
        tokens.signnumber = 1;
        tokens.amount = 0;
        tokens.tokenindex = 1;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        return tokens;
    }

    @Override
    public String toString() {
        return "Token [confirmed=" + confirmed + ", tokenid=" + tokenid + ", tokenindex=" + tokenindex + ", amount="
                + amount + ", tokenname=" + tokenname + ", description=" + description + ", url=" + url
                + ", signnumber=" + signnumber + ", multiserial=" + multiserial + ", tokentype=" + tokentype
                + ", tokenstop=" + tokenstop + ", prevblockhash=" + prevblockhash + ", blockhash=" + blockhash + "]";
    }
    
    
}
