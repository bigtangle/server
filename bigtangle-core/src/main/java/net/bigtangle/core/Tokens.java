/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

public class Tokens implements java.io.Serializable {

    private static final long serialVersionUID = 6992138619113601243L;

    public Tokens(String tokenid, String tokenname) {
        this.tokenid = tokenid;
        this.tokenname = tokenname;
    }

    public Tokens() {
    }

    private String blockhash;
    private boolean confirmed;
    private String tokenid;
    private int tokenindex;
    private long amount;
    private String tokenname;
    private String description;
    private String url;
    private int signnumber;
    private boolean multiserial;
    private int tokentype;
    private boolean tokenstop;
    // for check if solidity
    private String prevblockhash;

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

    public int getTokenindex() {
        return tokenindex;
    }

    public void setTokenindex(int tokenindex) {
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

    public static Tokens buildSimpleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, 
            int signnumber, int tokenindex, long amount, boolean multiserial, boolean tokenstop) {
        Tokens tokens = new Tokens();
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

    public static Tokens buildMarketTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, String url) {
        Tokens tokens = new Tokens();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setUrl(url);
        tokens.tokenstop = true;
        tokens.multiserial = false;
        tokens.tokentype = TokenType.market.ordinal();
        tokens.signnumber = 1;
        tokens.amount = 0;
        tokens.tokenindex = 1;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        return tokens;
    }

    public static Tokens buildSubtangleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname, String description, String url) {
        Tokens tokens = new Tokens();
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
}
