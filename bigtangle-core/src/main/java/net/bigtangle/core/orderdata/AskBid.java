package net.bigtangle.core.orderdata;

public class AskBid {

    private String tradesystem;

    private long eventTime;

    private String symbol;

    private PriceLevel askBest;
    private PriceLevel bidBest;

    public String getTradesystem() {
        return tradesystem;
    }

    public void setTradesystem(String tradesystem) {
        this.tradesystem = tradesystem;
    }

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public PriceLevel getAskBest() {
        return askBest;
    }

    public void setAskBest(PriceLevel askBest) {
        this.askBest = askBest;
    }

    public PriceLevel getBidBest() {
        return bidBest;
    }

    public void setBidBest(PriceLevel bidBest) {
        this.bidBest = bidBest;
    }

    @Override
    public String toString() {
        return "AskBid [tradesystem=" + tradesystem + ", eventTime=" + eventTime + ", symbol=" + symbol + ", askBest="
                + askBest + ", bidBest=" + bidBest + "]";
    }

}
