package net.bigtangle.userpay;

import java.math.BigDecimal;

public class EthBalance {

    private String  token;
    private String  ethaddress;
    private BigDecimal balance = BigDecimal.ZERO;
    
    
    
    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public String getEthaddress() {
        return ethaddress;
    }
    public void setEthaddress(String ethaddress) {
        this.ethaddress = ethaddress;
    }
    public BigDecimal getBalance() {
        return balance;
    }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    @Override
    public String toString() {
        return "EthBalance [token=" + token + ", ethaddress=" + ethaddress + ", balance=" + balance + "]";
    }
   

}
