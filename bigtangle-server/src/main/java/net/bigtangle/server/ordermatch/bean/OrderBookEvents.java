/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.bean;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.fraction.Fraction;

public class OrderBookEvents implements OrderBookListener {

    private List<Event> events;

    public OrderBookEvents() {
        this.events = new ArrayList<>();
    }

    public List<Event> collect() {
        return events;
    }

    @Override
    public void match(String restingOrderId, String incomingOrderId, Side incomingSide, Fraction price, long executedQuantity,
            long remainingQuantity) {
        events.add(
                new Match(restingOrderId, incomingOrderId, incomingSide, price, executedQuantity, remainingQuantity));
    }

    @Override
    public void add(String orderId, Side side, Fraction price, long size) {
        events.add(new Add(orderId, side, price, size));
    }

    @Override
    public void cancel(String orderId, long canceledQuantity, long remainingQuantity) {
        events.add(new Cancel(orderId, canceledQuantity, remainingQuantity));
    }

    public interface Event {
    }

    public static class Match implements Event {
        public final String restingOrderId;
        public final String incomingOrderId;
        public final Side incomingSide;
        public final Fraction price;
        public final long executedQuantity;
        public final long remainingQuantity;

        public Match(String restingOrderId, String incomingOrderId, Side incomingSide, Fraction price, long executedQuantity,
                long remainingQuantity) {
            this.restingOrderId = restingOrderId;
            this.incomingOrderId = incomingOrderId;
            this.incomingSide = incomingSide;
            this.price = price;
            this.executedQuantity = executedQuantity;
            this.remainingQuantity = remainingQuantity;
        }
        
        public Match(long restingOrderId, long incomingOrderId, Side incomingSide, Fraction price, long executedQuantity,
                long remainingQuantity) {
            this(String.valueOf(restingOrderId), String.valueOf(incomingOrderId), incomingSide, price, executedQuantity, remainingQuantity);
        }

        @Override
        public String toString() {
            return "Match [restingOrderId=" + restingOrderId + ", incomingOrderId=" + incomingOrderId
                    + ", incomingSide=" + incomingSide + ", price=" + price + ", executedQuantity=" + executedQuantity
                    + ", remainingQuantity=" + remainingQuantity + "]";
        }
    }

    public static class Add implements Event {
        public final String orderId;
        public final Side side;
        public final Fraction price;
        public final long size;

        public Add(String orderId, Side side, Fraction price, long size) {
            this.orderId = orderId;
            this.side = side;
            this.price = price;
            this.size = size;
        }
        
        public Add(long orderId, Side side, Fraction price, long size) {
            this(String.valueOf(orderId), side, price, size);
        }

        @Override
        public String toString() {
            return "Add [orderId=" + orderId + ", side=" + side + ", price=" + price + ", size=" + size + "]";
        }
    }

    public static class Cancel implements Event {
        public final String orderId;
        public final long canceledQuantity;
        public final long remainingQuantity;

        public Cancel(String orderId, long canceledQuantity, long remainingQuantity) {
            this.orderId = orderId;
            this.canceledQuantity = canceledQuantity;
            this.remainingQuantity = remainingQuantity;
        }
        
        public Cancel(long orderId, long canceledQuantity, long remainingQuantity) {
            this(String.valueOf(orderId), canceledQuantity, remainingQuantity);
        }

        @Override
        public String toString() {
            return "Cancel [orderId=" + orderId + ", canceledQuantity=" + canceledQuantity + ", remainingQuantity=" + remainingQuantity + "]";
        }
    }

}
