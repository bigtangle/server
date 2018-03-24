package com.bignetcoin.ui.wallet;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class StockModel {
    private SimpleStringProperty stockName;
    private SimpleStringProperty stockCode;
    private SimpleLongProperty stockNumber;
    private SimpleStringProperty stockDescription;

    public StockModel(String name, String code, long number, String description) {
        this.stockName = new SimpleStringProperty(name);
        this.stockCode = new SimpleStringProperty(code);
        this.stockNumber = new SimpleLongProperty(number);
        this.stockDescription = new SimpleStringProperty(description);
    }

    public SimpleStringProperty stockName() {
        return stockName;
    }

    public SimpleStringProperty stockCode() {
        return stockCode;
    }

    public SimpleStringProperty stockDescription() {
        return stockDescription;
    }

    public SimpleLongProperty stockNumber() {
        return stockNumber;
    }

    public String getStockName() {
        return stockName.get();
    }

    public void setStockName(String stockName) {
        this.stockName.set(stockName);
    }

    public String getStockCode() {
        return stockCode.get();
    }

    public void setStockCode(String stockCode) {
        this.stockCode.set(stockCode);
    }

    public long getStockNumber() {
        return stockNumber.get();
    }

    public void setStockNumber(long stockNumber) {
        this.stockNumber.set(stockNumber);
    }

    public String getStockDescription() {
        return stockDescription.get();
    }

    public void setStockDescription(String stockDescription) {
        this.stockDescription.set(stockDescription);
    }
}
