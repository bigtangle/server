package net.bigtangle.server.response;

public class GenesisBlockLRResponse extends AbstractResponse {
    
    public static AbstractResponse create(String leftBlockHex, String rightBlockHex) {
        GenesisBlockLRResponse res = new GenesisBlockLRResponse();
        res.leftBlockHex = leftBlockHex;
        res.rightBlockHex = rightBlockHex;
        return res;
    }

    private String rightBlockHex;
    
    private String leftBlockHex;

    public String getRightBlockHex() {
        return rightBlockHex;
    }

    public void setRightBlockHex(String rightBlockHex) {
        this.rightBlockHex = rightBlockHex;
    }

    public String getLeftBlockHex() {
        return leftBlockHex;
    }

    public void setLeftBlockHex(String leftBlockHex) {
        this.leftBlockHex = leftBlockHex;
    }
}
