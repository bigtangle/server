/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.order.match;

import java.util.ArrayList;

class PriceLevel {

    private Side side;

    private long price;

    private ArrayList<Order> orders;

    public PriceLevel(Side side, long price) {
        this.side   = side;
        this.price  = price;
        this.orders = new ArrayList<>();
    }

    public Side getSide() {
        return side;
    }

    public long getPrice() {
        return price;
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public Order add(String orderId, long size) {
        Order order = new Order(this, orderId, size);

        orders.add(order);

        return order;
    }

    public long match(String orderId, Side side, long quantity, OrderBookListener listener) {
        while (quantity > 0 && !orders.isEmpty()) {
            Order order = orders.get(0);

            long orderQuantity = order.getRemainingQuantity();

            if (orderQuantity > quantity) {
                order.reduce(quantity);

                listener.match(order.getId(), orderId, side, price, quantity, order.getRemainingQuantity());

                quantity = 0;
            } else {
                orders.remove(0);

                listener.match(order.getId(), orderId, side, price, orderQuantity, 0);

                quantity -= orderQuantity;
            }
        }

        return quantity;
    }

    public void delete(Order order) {
        orders.remove(order);
    }

}
