package wallettemplate;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;

public class CoinModel {
    private SimpleLongProperty value;
    private SimpleIntegerProperty tokenid;

    public SimpleLongProperty value() {
        return value;
    }

    public SimpleIntegerProperty tokenid() {
        return tokenid;
    }

    public long getValue() {
        return value.get();
    }

    public void setValue(long value) {
        this.value.set(value);
    }

    public int getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(int tokenid) {
        this.tokenid.set(tokenid);
    }

    public CoinModel(long value, int tokenid) {
        super();
        this.value = new SimpleLongProperty(value);
        this.tokenid = new SimpleIntegerProperty(tokenid);
    }

}
