package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
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

    // Keeps last n prices of matching events
    private Map<String, List<Long>> tokenIds2LastMatchedPrices = new HashMap<>();

    private static final int LIST_SIZE = 5;

    public OrderTickerService() {
        // TODO optional: load persistent state from db (must be written first)
    }

    public void updatePrices(String tokenId, List<Event> events) {
        synchronized (tokenIds2LastMatchedPrices) {
            if (tokenIds2LastMatchedPrices.get(tokenId) == null)
                tokenIds2LastMatchedPrices.put(tokenId, new ArrayList<>());
            List<Long> lastMatchedPrices = tokenIds2LastMatchedPrices.get(tokenId);

            for (Event ev : events) {
                if (ev instanceof Match) {
                    if (lastMatchedPrices.size() >= OrderTickerService.LIST_SIZE)
                        lastMatchedPrices.remove(0);
                    lastMatchedPrices.add(((Match) ev).price);
                }
            }
        }
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
     */
    public List<Long> getLastMatchedPrices(String tokenId) {
        synchronized (tokenIds2LastMatchedPrices) {
            List<Long> lastMatchedPrices = tokenIds2LastMatchedPrices.get(tokenId);
            return lastMatchedPrices == null ? new ArrayList<>() : new ArrayList<>(lastMatchedPrices);
        }
    }

    /**
     * Returns the last matched price or null if there is none
     * @param tokenId ID of token
     * @return the last matched price or null if there is none
     */
    public Long getLastMatchedPrice(String tokenId) {
        List<Long> lastMatchedPrices = getLastMatchedPrices(tokenId);
        return lastMatchedPrices.size() == 0 ? null : lastMatchedPrices.get(lastMatchedPrices.size() - 1);
    }
}
