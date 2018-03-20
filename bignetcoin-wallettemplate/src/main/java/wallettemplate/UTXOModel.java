package wallettemplate;

import org.bitcoinj.core.NetworkParameters;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

public class UTXOModel {
    private SimpleDoubleProperty balance;
    private SimpleStringProperty tokentype;
    private SimpleStringProperty address;

    public UTXOModel(double balance, int tokenid, String address) {
        this.balance = new SimpleDoubleProperty(balance);
        this.tokentype = new SimpleStringProperty(tokenid == NetworkParameters.BIGNETCOIN_TOKENID ? "bignetcoin" : "other");
        this.address = new SimpleStringProperty(address);
    }

    public SimpleDoubleProperty balance() {
        return balance;
    }

    public SimpleStringProperty tokentype() {
        return tokentype;
    }

    public SimpleStringProperty address() {
        return address;
    }

    public double getBalance() {
        return balance.get();
    }

    public void setBalance(double balance) {
        this.balance.set(balance);
    }

    public String getTokentype() {
        return tokentype.get();
    }

    public void setTokentype(String tokentype) {
        this.tokentype.set(tokentype);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

}
