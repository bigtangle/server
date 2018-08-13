/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

public class Tokens implements java.io.Serializable {

    private static final long serialVersionUID = 6992138619113601243L;

    public Tokens(String tokenid, String tokenname, String description, String url, long signnumber,
            boolean multiserial, int tokenType, boolean tokenstop) {
        this.tokenid = tokenid;
        this.tokenname = tokenname;
        this.description = description;
        this.url = url;
        this.signnumber = signnumber;
        this.multiserial = multiserial;
        this.tokenType = tokenType;
        this.tokenstop = tokenstop;
    }

    public Tokens(String tokenid, String tokenname) {
        this.tokenid = tokenid;
        this.tokenname = tokenname;

    }

    public Tokens() {
        super();
    }

    private String tokenid;

    private String tokenname;

    private String description;

    private String url;

    private long signnumber;

    private boolean multiserial;

    private int tokenType;

    public int getTokenType() {
        return tokenType;
    }

    public void setTokenType(int tokenType) {
        this.tokenType = tokenType;
    }

    private boolean tokenstop;

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
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

    public long getSignnumber() {
        return signnumber;
    }

    public void setSignnumber(long signnumber) {
        this.signnumber = signnumber;
    }

    public boolean isMultiserial() {
        return multiserial;
    }

    public void setMultiserial(boolean multiserial) {
        this.multiserial = multiserial;
    }

    public boolean isTokenstop() {
        return tokenstop;
    }

    public void setTokenstop(boolean tokenstop) {
        this.tokenstop = tokenstop;
    }

    @Override
    public String toString() {
        return "Tokens [tokenid=" + tokenid + ", tokenname=" + tokenname + ", description=" + description + ", url="
                + url + ", signnumber=" + signnumber + ", multiserial=" + multiserial + ", tokenType=" + tokenType
                + ", tokenstop=" + tokenstop + "]";
    }

    public Tokens copy(Tokens tokens) {
        this.tokenid = tokens.getTokenid();
        this.tokenname = tokens.getTokenname();
        this.description = tokens.getDescription();
        this.url = tokens.getUrl();
        this.signnumber = tokens.getSignnumber();
        this.multiserial = tokens.isMultiserial();
        this.tokenType = tokens.getTokenType();
        this.tokenstop = tokens.isTokenstop();
        return this;
    }

}
