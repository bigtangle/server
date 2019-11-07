/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.math.BigInteger;

public class Token extends SpentBlock implements java.io.Serializable {

    private static final long serialVersionUID = 6992138619113601243L;

    
    // Token config 
    public static final int TOKEN_MAX_NAME_LENGTH = 100;
    public static final int TOKEN_MAX_DESC_LENGTH = 5000;
    public static final int TOKEN_MAX_URL_LENGTH = 100;
    public static final int TOKEN_MAX_ID_LENGTH = 100;
    public static final int TOKEN_MAX_LANGUAGE_LENGTH = 2;
    public static final int TOKEN_MAX_CLASSIFICATION_LENGTH = 100;
 
    
    public static Token genesisToken(NetworkParameters params) {
      Token  genesisToken= Token.buildSimpleTokenInfo(true, null, NetworkParameters.BIGTANGLE_TOKENID_STRING,
            NetworkParameters.BIGTANGLE_TOKENNAME, "BigTangle Currency", 1, 0,
            NetworkParameters.BigtangleCoinTotal, true, NetworkParameters.BIGTANGLE_DECIMAL, "");
        genesisToken.setBlockHash(params.getGenesisBlock().getHash());
        genesisToken.setTokentype(TokenType.currency.ordinal());
     return genesisToken;
             
    }
    
    public static Token buildSimpleTokenInfo(boolean confirmed, Sha256Hash prevblockhash, String tokenid, String tokenname,
            String description, int signnumber, long tokenindex, BigInteger amount, boolean tokenstop, int decimals,
            String predecessingDomainBlockHash) {

        return buildSimpleTokenInfo(confirmed, prevblockhash, tokenid, tokenname, description, signnumber, tokenindex,
                amount, tokenstop, null, false, null, null, TokenType.token.ordinal(), decimals, null,
                predecessingDomainBlockHash);
    }

    public static Token buildDomainnameTokenInfo(boolean confirmed, Sha256Hash prevblockhash, String tokenid,
            String tokenname, String description, int signnumber, long tokenindex,  boolean tokenstop,
           String domainname, String predecessingDomainBlockHash) {

        Token token = buildSimpleTokenInfo(confirmed, prevblockhash, tokenid, tokenname, description, signnumber,
                tokenindex, new BigInteger("1"), tokenstop, null, false, null, null, TokenType.domainname.ordinal(), 0,
                domainname, predecessingDomainBlockHash);

        return token;
    }

    public Token(String tokenid, String tokenname) {
        this.tokenid = tokenid;
        this.tokenname = tokenname;
    }

    public Token() {
    }

    private String tokenid;
    // the index for token in serial
    private long tokenindex;
    // name of the token
    private String tokenname;
    // description
    private String description;
    // The domain name or parent domain name defined by this token, default "" or null for root domain, 
    //if the token is domainname type, then it is the parent domain name
    //for example type=domainname, then tokenname=bigtangle, here is domainname=bc, 
    //the full domainname is bigtangle.bc, otherwise it is a token in this domain name 
    private String domainName="";
    // the  domainName's block hash, 
    //"" or null as root domain, it will be networkParameters.getGenesisBlock().getHashAsString()
    private String domainNameBlockHash;
    // number of signature
    private int signnumber;
    // difference type of token 
    private int tokentype;
    // if this is true, there is no possible to change the token anymore.
    private boolean tokenstop;
    // indicator of the prev token index blockhash
    private Sha256Hash prevblockhash;

 
    private BigInteger amount;
    private int decimals = 0; // number of decimals for the token, default
                              // integer
    // classification of a token, can be null, optional for query only
    private String classification;
    // language of the token, can be null, optional for query only
    private String language;

    // disable the token usage,
    // But it is only informative for new transaction with the token, not in
    // consensus.
    private Boolean revoked = false;

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

    public BigInteger getAmount() {
        return amount;
    }

    public void setAmount(BigInteger amount) {
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

    public String getDomainName() {
        if(domainName==null) domainName="";
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }
 

    public String getDomainNameBlockHash() {
        return domainNameBlockHash;
    }

    public void setDomainNameBlockHash(String domainNameBlockHash) {
        this.domainNameBlockHash = domainNameBlockHash;
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

    public Sha256Hash getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(Sha256Hash prevblockhash) {
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

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }

    public String getTokennameDisplay() {
        if (domainName == null || "null".equals(domainName) || "".equals(domainName) )
            return tokenname;
        else {
            return tokenname + "@" + domainName;
        }
    }

    public static Token buildSimpleTokenInfo(boolean confirmed, Sha256Hash prevblockhash, String tokenid, String tokenname,
            String description, int signnumber, long tokenindex, BigInteger amount, boolean tokenstop,
            TokenKeyValues tokenKeyValues, Boolean revoked, String language, String classification, int tokentype,
            int decimals, final String domainName, final String domainNameBlockHash) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.tokenstop = tokenstop;
        tokens.tokentype = tokentype;
        tokens.signnumber = signnumber;
        tokens.amount = amount;
        tokens.tokenindex = tokenindex;
        tokens.setConfirmed ( confirmed);
        tokens.prevblockhash = prevblockhash;
        tokens.tokenKeyValues = tokenKeyValues;
        tokens.revoked = revoked;
        tokens.language = language;
        tokens.classification = classification;
        tokens.decimals = decimals;
        tokens.domainName = domainName;
        tokens.domainNameBlockHash = domainNameBlockHash;
        return tokens;
    }

    public static Token buildMarketTokenInfo(boolean confirmed, Sha256Hash prevblockhash, String tokenid, String tokenname,
            String description, String domainname) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setDomainName(domainname);
        tokens.tokenstop = true;

        tokens.tokentype = TokenType.market.ordinal();
        tokens.signnumber = 1;
        tokens.amount = BigInteger.ZERO;
        tokens.tokenindex = 0;
        tokens.setConfirmed ( confirmed);
        tokens.prevblockhash = prevblockhash;
 
        return tokens;
    }

    public static Token buildSubtangleTokenInfo(boolean confirmed, Sha256Hash prevblockhash, String tokenid,
            String tokenname, String description, String domainname) {
        Token tokens = new Token();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setDescription(description);
        tokens.setDomainName(domainname);
        tokens.tokenstop = true;
        tokens.tokentype = TokenType.subtangle.ordinal();
        tokens.signnumber = 1;
        tokens.amount = BigInteger.ZERO;
        tokens.tokenindex = 1;
        tokens.setConfirmed ( confirmed);
        tokens.prevblockhash = prevblockhash;

        return tokens;
    }


  
}
