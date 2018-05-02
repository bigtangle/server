package net.bigtangle.tools.account;

public class RandomTrade {

    private String address;
    
    private String tokenID;

    public String getAddress() {
        return address;
    }

    public String getTokenID() {
        return tokenID;
    }

    public RandomTrade(String address, String tokenID) {
        this.address = address;
        this.tokenID = tokenID;
    }
}
