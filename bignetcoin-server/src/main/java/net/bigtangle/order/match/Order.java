package net.bigtangle.order.match;

class Order {

    private PriceLevel level;

    private long id;

    private long remainingQuantity;

    public Order(PriceLevel level, long id, long size) {
        this.level = level;

        this.id = id;

        this.remainingQuantity = size;
    }

    public PriceLevel getLevel() {
        return level;
    }

    public long getId() {
        return id;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public void reduce(long quantity) {
        remainingQuantity -= quantity;
    }

    public void resize(long size) {
        remainingQuantity = size;
    }

}
