/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.model;

import java.math.BigInteger;

import net.bigtangle.core.Token;
import net.bigtangle.core.Utils;

public class TokenModel implements java.io.Serializable {

    private static final long serialVersionUID = 6992138619113601243L;

    private String tokenid;
    // the index for token in serial
    private long tokenindex;
    // name of the token
    private String tokenname;
    // description
    private String description;

    private String domainname;
    private String domainnameblockHash;
    // number of signature
    private Integer signnumber;
    // difference type of token
    private Integer tokentype;
    // if this is true, there is no possible to change the token anymore.
    private Boolean tokenstop;
    // indicator of the prev token index blockhash
    private String prevblockhash;

    private BigInteger amount;
    private Integer decimals = 0; // number of decimals for the token, default
    // integer
    // classification of a token, can be null, optional for query only
    private String classification;
    // language of the token, can be null, optional for query only
    private String language;

    private Boolean revoked = false;
    private Boolean confirmed = false;
    // Token contains any other type of data as key value, application may save
    // customer data as json for communication between systems
    // It can be saved in a NoSQL database as key value pair for query
    private String tokenkeyvalues;

    public static TokenModel fromToken(Token token) {
        TokenModel tokenModels = new TokenModel();
        tokenModels.setTokenid(token.getTokenid());
        tokenModels.setTokenname(token.getTokenname());
        tokenModels.setDescription(token.getDescription());
        tokenModels.setTokenstop(token.isTokenstop());
        tokenModels.setTokentype(token.getTokentype());
        tokenModels.setSignnumber(token.getSignnumber());
        tokenModels.setAmount(token.getAmount());
        tokenModels.tokenindex = token.getTokenindex();
        tokenModels.confirmed = token.isConfirmed();
        tokenModels.prevblockhash = Utils.HEX.encode(token.getPrevblockhash().getBytes());
        tokenModels.tokenkeyvalues = Utils.HEX.encode(token.getTokenKeyValues().toByteArray());
        tokenModels.revoked = token.getRevoked();
        tokenModels.language = token.getLanguage();
        // tokenModels.classification = token.getClassification();
        tokenModels.decimals = token.getDecimals();
        tokenModels.domainname = token.getDomainName();
        tokenModels.domainnameblockHash = token.getDomainNameBlockHash();
        return tokenModels;
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

    public String getDomainnameblockHash() {
        return domainnameblockHash;
    }

    public void setDomainnameblockHash(String domainnameblockHash) {
        this.domainnameblockHash = domainnameblockHash;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public Integer getSignnumber() {
        return signnumber;
    }

    public void setSignnumber(Integer signnumber) {
        this.signnumber = signnumber;
    }

    public Integer getTokentype() {
        return tokentype;
    }

    public void setTokentype(Integer tokentype) {
        this.tokentype = tokentype;
    }

    public Boolean getTokenstop() {
        return tokenstop;
    }

    public void setTokenstop(Boolean tokenstop) {
        this.tokenstop = tokenstop;
    }

    public String getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(String prevblockhash) {
        this.prevblockhash = prevblockhash;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public void setAmount(BigInteger amount) {
        this.amount = amount;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
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

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public String getTokenkeyvalues() {
        return tokenkeyvalues;
    }

    public void setTokenkeyvalues(String tokenkeyvalues) {
        this.tokenkeyvalues = tokenkeyvalues;
    }

}
