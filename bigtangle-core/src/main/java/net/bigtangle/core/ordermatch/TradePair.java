package net.bigtangle.core.ordermatch;

public class TradePair implements Comparable<TradePair> {

    /*
     * for example buy YUAN in USD, orderToken=YUAN, orderBaseToken=USD sell
     * YUAN in USD, orderToken=YUAN, orderBaseToken=USD or Buy YUAN in BIG,
     * orderToken=YUAN, orderBaseToken=bc buy USD in YUAN, orderToken=USD,
     * orderBaseToken=YUAN There is no match in different based Token, such as
     * buy YUAN in USD and sell USD in YUAN
     */
    // Trade in pair,
    private String orderToken;
    // Base token for the order
    private String orderBaseToken;

    public TradePair(String orderToken, String orderBaseToken) {
        super();
        this.orderToken = orderToken;
        this.orderBaseToken = orderBaseToken;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((orderBaseToken == null) ? 0 : orderBaseToken.hashCode());
        result = prime * result + ((orderToken == null) ? 0 : orderToken.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TradePair other = (TradePair) obj;
        if (orderBaseToken == null) {
            if (other.orderBaseToken != null)
                return false;
        } else if (!orderBaseToken.equals(other.orderBaseToken))
            return false;
        if (orderToken == null) {
            if (other.orderToken != null)
                return false;
        } else if (!orderToken.equals(other.orderToken))
            return false;
        return true;
    }

    public String getOrderToken() {
        return orderToken;
    }

    public void setOrderToken(String orderToken) {
        this.orderToken = orderToken;
    }

    public String getOrderBaseToken() {
        return orderBaseToken;
    }

    public void setOrderBaseToken(String orderBaseToken) {
        this.orderBaseToken = orderBaseToken;
    }

    @Override
    public int compareTo(TradePair other) {
        int last = this.getOrderToken().compareTo(other.getOrderToken());
        return last == 0 ? this.orderBaseToken.compareTo(other.orderBaseToken) : last;

    }

}
