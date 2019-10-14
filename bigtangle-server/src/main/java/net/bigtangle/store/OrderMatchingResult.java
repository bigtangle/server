package net.bigtangle.store;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;

public class OrderMatchingResult {
    Set<OrderRecord> spentOrders;
    byte[] outputTx;
    Collection<OrderRecord> remainingOrders;
    @JsonIgnore
    Map<String, List<Event>> tokenId2Events;

    public OrderMatchingResult() {

    }

    public OrderMatchingResult(Set<OrderRecord> spentOrders, Transaction outputTx,
            Collection<OrderRecord> remainingOrders, Map<String, List<Event>> tokenId2Events) {
        this.spentOrders = spentOrders;
        this.outputTx = outputTx.bitcoinSerialize();
        this.remainingOrders = remainingOrders;
        this.tokenId2Events = tokenId2Events;

    }

    public Transaction getOutputTx(NetworkParameters networkParameters) {
        return (Transaction) networkParameters.getDefaultSerializer().makeTransaction(getOutputTx());
    }

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static OrderMatchingResult parseChecked(byte[] buf) {
        try {
            return OrderMatchingResult.parse(buf);
        } catch (IOException e) {

            throw new RuntimeException(e);
        }
    }

    public static OrderMatchingResult parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderMatchingResult tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderMatchingResult.class);
        return tokenInfo;
    }

    public Set<OrderRecord> getSpentOrders() {
        return spentOrders;
    }

    public void setSpentOrders(Set<OrderRecord> spentOrders) {
        this.spentOrders = spentOrders;
    }

    public byte[] getOutputTx() {
        return outputTx;
    }

    public void setOutputTx(byte[] outputTx) {
        this.outputTx = outputTx;
    }

    public Collection<OrderRecord> getRemainingOrders() {
        return remainingOrders;
    }

    public void setRemainingOrders(Collection<OrderRecord> remainingOrders) {
        this.remainingOrders = remainingOrders;
    }

    public Map<String, List<Event>> getTokenId2Events() {
        return tokenId2Events;
    }

    public void setTokenId2Events(Map<String, List<Event>> tokenId2Events) {
        this.tokenId2Events = tokenId2Events;
    }

 
     

}