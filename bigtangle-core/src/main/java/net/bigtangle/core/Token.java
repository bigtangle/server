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

    // indicator, if the token block is confirmed
    private boolean confirmed;
    private String tokenid;
    // the index for token in serial
    private long tokenindex;
    // name of the token
    private String tokenname;
    // description
    private String description;
    // the  domain name of this token
    private String domainname;
    // the Tokenid of the domain name
    private String domainnameTokenid;
    // number of signature
    private int signnumber;
    // difference for external exchange, meta token, digital asset token and
    // substangle
    private int tokentype;
    // if this is true, there is no possible to change the token anymore.
    private boolean tokenstop;
    // indicator of the prev token index blockhash
    private String prevblockhash;

    private String blockhash; // TODO slated for extraction
    private long amount; // TODO must be inferred on insertion, slated for
                         // extraction

    // classification of a token, can be null, optional for query only
    private String classification;
    // language of the token, can be null, optional for query only
    private String language;

    // disable the token usage,
    // But it is only informative for new transaction with the token, not in consensus.
    private Boolean revoked=false;

    // Token contains any other type of data as key value, application may save
    // customer data as json for communication between systems
    // It can be saved in a NoSQL database as key value pair for query
    private TokenKeyValues tokenKeyValues;

    public void addKeyvalue(KeyValue kv) {
        if (tokenKeyValues == null) {
            tokenKeyValues = new TokenKeyValues();
        }
        tokenKeyValues.addKeyvalue(kv);
    }

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

 

    public String getDomainname() {
        return domainname;
    }

    public void setDomainname(String domainname) {
        this.domainname = domainname;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public int getSignnumber() {
        return signnumber;
    }

    public void setSignnumber(int signnumber) {
        this.signnumber = signnumber;
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

    public TokenKeyValues getTokenKeyValues() {
        return tokenKeyValues;
    }

    public void setTokenKeyValues(TokenKeyValues tokenKeyValues) {
        this.tokenKeyValues = tokenKeyValues;
    }

 
    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDomainnameTokenid() {
        return domainnameTokenid;
    }

    public void setDomainnameTokenid(String domainnameTokenid) {
        this.domainnameTokenid = domainnameTokenid;
    }

    public static Token buildSimpleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname,
            String description, int signnumber, long tokenindex, long amount, boolean tokenstop) {

        return buildSimpleTokenInfo(confirmed, prevblockhash, tokenid, tokenname, description, signnumber, tokenindex,
                amount, tokenstop, null, false, null, null, TokenType.token.ordinal());
    }

    public static Token buildDomainnameTokenInfo(boolean confirmed, String prevblockhash, String tokenid,
            String tokenname, String description, int signnumber, long tokenindex, long amount, boolean tokenstop) {

        return buildSimpleTokenInfo(confirmed, prevblockhash, tokenid, tokenname, description, signnumber, tokenindex,
                amount, tokenstop, null, false, null, null, TokenType.domainname.ordinal());
    }

    public static Token buildSimpleTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname,
            String description, int signnumber, long tokenindex, long amount, boolean tokenstop,
            TokenKeyValues tokenKeyValues, Boolean revoked, String language, String classification,
            int tokentype) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.tokenstop = tokenstop;

        tokens.tokentype = tokentype; // ;
        tokens.signnumber = signnumber;
        tokens.amount = amount;
        tokens.tokenindex = tokenindex;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        tokens.tokenKeyValues = tokenKeyValues;
        tokens.revoked = revoked;
        tokens.language = language;
        tokens.classification = classification;
        return tokens;
    }

    public static Token buildMarketTokenInfo(boolean confirmed, String prevblockhash, String tokenid, String tokenname,
            String description, String domainname) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setDomainname(domainname);
        tokens.tokenstop = true;

        tokens.tokentype = TokenType.market.ordinal();
        tokens.signnumber = 1;
        tokens.amount = 0;
        tokens.tokenindex = 0;
        tokens.confirmed = confirmed;
        tokens.prevblockhash = prevblockhash;
        return tokens;
    }

    public static Token buildSubtangleTokenInfo(boolean confirmed, String prevblockhash, String tokenid,
            String tokenname, String description, String domainname) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setDomainname(domainname);
        tokens.tokenstop = true;
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
        return "Token [confirmed=" + confirmed + ", tokenid=" + tokenid + ", tokenindex=" + tokenindex + ", tokenname="
                + tokenname + ", description=" + description + ", url=" + domainname + ", signnumber=" + signnumber
                + ", tokentype=" + tokentype + ", tokenstop=" + tokenstop + ", prevblockhash=" + prevblockhash
                + ", blockhash=" + blockhash + ", amount=" + amount + ", revoked=" + revoked
                + ", classification=" + classification + ", language=" + language + ", tokenKeyValues=" + tokenKeyValues
                + "]";
    }

}
