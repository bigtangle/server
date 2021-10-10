package net.bigtangle.core.orderdata;

import java.math.BigDecimal;

public class PriceLevel {

    private BigDecimal price;

    private BigDecimal amount;

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "PriceLevel [price=" + price + ", amount=" + amount + "]";
    }

}
