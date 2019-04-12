package net.bigtangle.server.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Event;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Match;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * This service provides informations on current exchange rates
 */
@Service
public class OrderTickerService {

    @Autowired
    protected FullPrunedBlockStore store;

    public void addMatchingEvents(Transaction outputTx, Map<String, List<Event>> tokenId2Events) throws BlockStoreException {
        for (Entry<String, List<Event>> entry : tokenId2Events.entrySet()) {
            for (Event event : entry.getValue()) {
                if (event instanceof Match) {
                    store.insertMatchingEvent(outputTx.getHash(), entry.getKey(), ((Match) event), System.currentTimeMillis());
                }
            }
        }
    }

    public void removeMatchingEvents(Transaction outputTx, Map<String, List<Event>> tokenId2Events) throws BlockStoreException {
        store.deleteMatchingEvents(outputTx.getHash());
    }

    /**
     * Returns up to n best currently open sell orders
     * @param tokenId the id of the token
     * @return up to n best currently open sell orders
     * @throws BlockStoreException
     */
    public List<OrderRecord> getBestOpenSellOrders(String tokenId, int count) throws BlockStoreException {
        return store.getBestOpenSellOrders(tokenId, count);
    }

    /**
     * Returns up to n best currently open buy orders
     * @param tokenId the id of the token
     * @return up to n best currently open buy orders
     * @throws BlockStoreException
     */
    public List<OrderRecord> getBestOpenBuyOrders(String tokenId, int count) throws BlockStoreException {
        return store.getBestOpenBuyOrders(tokenId, count);
    }

    /**
     * Returns a list of up to n last prices in ascending occurrence
     * @param tokenId ID of token
     * @return a list of up to n last prices in ascending occurrence
     * @throws BlockStoreException 
     */
    public List<Match> getLastMatchingEvents(String tokenId, int count) throws BlockStoreException {
        // Count is limited:
        if (count > 500)
            count = 500;
        
        return store.getLastMatchingEvents(tokenId, count);
    }
}
