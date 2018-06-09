package net.bigtangle.core;

public class PayMultiSignExt extends PayMultiSign {

    private static final long serialVersionUID = -4724601007065748377L;
    
    private int sign;
    
    private int realSignnumber;

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public int getRealSignnumber() {
        return realSignnumber;
    }

    public void setRealSignnumber(int realSignnumber) {
        this.realSignnumber = realSignnumber;
    }
}
