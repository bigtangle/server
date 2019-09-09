package net.bigtangle.server.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.server.resp.OrderTickerResponse;
import net.bigtangle.server.ordermatch.bean.MatchResult;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Event;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents.Match;
import net.bigtangle.store.FullPrunedBlockGraph.OrderMatchingResult;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * This service provides informations on current exchange rates
 */
@Service
public class OrderTickerService {

    private int MAXCOUNT = 500;
    @Autowired
    protected FullPrunedBlockStore store;

    public void addMatchingEvents(OrderMatchingResult orderMatchingResult) throws BlockStoreException {
        // collect the spend order volumn and ticker to write to database
        // Map<String, MatchResult> matchResultList = new HashMap<String,
        // MatchResult>();
        for (Entry<String, List<Event>> entry : orderMatchingResult.getTokenId2Events().entrySet()) {
            for (Event event : entry.getValue()) {
                if (event instanceof Match) {
                    MatchResult f = new MatchResult(orderMatchingResult.getOutputTx().getHashAsString(), entry.getKey(),
                            ((OrderBookEvents.Match) event).price, ((OrderBookEvents.Match) event).executedQuantity,
                            System.currentTimeMillis());
                    store.insertMatchingEvent(f);
                }
            }
        }

    }

    public void removeMatchingEvents(Transaction outputTx, Map<String, List<Event>> tokenId2Events)
            throws BlockStoreException {
        store.deleteMatchingEvents(outputTx.getHashAsString());
    }

    /**
     * Returns up to n best currently open sell orders
     * 
     * @param tokenId
     *            the id of the token
     * @return up to n best currently open sell orders
     * @throws BlockStoreException
     */
    public List<OrderRecord> getBestOpenSellOrders(String tokenId, int count) throws BlockStoreException {
        return store.getBestOpenSellOrders(tokenId, count);
    }

    /**
     * Returns up to n best currently open buy orders
     * 
     * @param tokenId
     *            the id of the token
     * @return up to n best currently open buy orders
     * @throws BlockStoreException
     */
    public List<OrderRecord> getBestOpenBuyOrders(String tokenId, int count) throws BlockStoreException {
        return store.getBestOpenBuyOrders(tokenId, count);
    }

    /**
     * Returns a list of up to n last prices in ascending occurrence
     * 
     * @param tokenId
     *            ID of token
     * @return a list of up to n last prices in ascending occurrence
     * @throws BlockStoreException
     */
    public OrderTickerResponse getLastMatchingEvents(Set<String> tokenIds) throws BlockStoreException {

        List<MatchResult> re = store.getLastMatchingEvents(tokenIds, MAXCOUNT);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokename(re));

    }

    public Map<String, Token> getTokename(List<MatchResult> res) throws BlockStoreException {
        Set<String> tokenids = new HashSet<String>();
        for (MatchResult d : res) {
            tokenids.add(d.getTokenid());

        }
        Map<String, Token> re = new HashMap<String, Token>();
        List<Token> tokens = store.getTokensList(tokenids);
        for (Token t : tokens) {
            re.put(t.getTokenid(), t);
        }
        return re;
    }
}
