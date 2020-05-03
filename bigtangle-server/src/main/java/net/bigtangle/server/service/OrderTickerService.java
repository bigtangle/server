package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OrderRecordMatched;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.OrderMatchedResponse;
import net.bigtangle.core.response.OrderTickerResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.data.OrderMatchingResult;

/**
 * This service provides informations on current exchange rates
 */
@Service
public class OrderTickerService {

    private int MAXCOUNT = 500;
    @Autowired
    protected FullPrunedBlockStore store;

    public void addMatchingEvents(OrderMatchingResult orderMatchingResult, String transactionHash, long matchBlockTime)
            throws BlockStoreException {
        // collect the spend order volumn and ticker to write to database
        // Map<String, MatchResult> matchResultList = new HashMap<String,
        // MatchResult>();
        try {
            for (Entry<String, List<Event>> entry : orderMatchingResult.getTokenId2Events().entrySet()) {
                for (Event event : entry.getValue()) {
                    if (event instanceof Match) {
                        MatchResult f = new MatchResult(transactionHash, entry.getKey(),
                                ((OrderBookEvents.Match) event).price, ((OrderBookEvents.Match) event).executedQuantity,
                                matchBlockTime);
                        store.insertMatchingEvent(f);
                    }
                }
            }
            addOrderMatchingResultDB(orderMatchingResult, transactionHash, matchBlockTime);
        } catch (Exception e) {
            // this is analysis data and is not consensus relevant
        }
    }

    /*
     * for execution info, add the executed order record on database for
     * analysis It is saved with the matchBlockTime and will be deleted after
     * days
     */
    public void addOrderMatchingResultDB(OrderMatchingResult orderMatchingResult, String transactionHash,
            long matchBlockTime) throws BlockStoreException {
        List<OrderRecordMatched> olist = new ArrayList<OrderRecordMatched>();
        for (OrderRecord o : orderMatchingResult.getSpentOrders()) {
            OrderRecordMatched f = OrderRecordMatched.fromOrderRecord(o);
            f.setTransactionHash(transactionHash);
            f.setMatchBlockTime(matchBlockTime);
            olist.add(f);
        }
        store.insertOrderRecordMatched(olist);
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

    // @Cacheable(cacheNames = "priceticker")
    public OrderTickerResponse getLastMatchingEvents(Set<String> tokenIds, int count) throws BlockStoreException {
        List<MatchResult> re = store.getLastMatchingEvents(tokenIds, count);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokename(re));

    }

    // @CacheEvict(cacheNames = "priceticker")
    public void evictAllCacheValues() {
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

    @Cacheable("priceticker")
    public AbstractResponse getTimeBetweenMatchingEvents(Set<String> tokenids, Long startDate, Long endDate)
            throws BlockStoreException {
        List<MatchResult> re = store.getTimeBetweenMatchingEvents(tokenids, startDate, endDate, MAXCOUNT);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokename(re));
    }

    public OrderMatchedResponse getOrderRecordMatched(String tokenId, String matchBlockTime)
            throws BlockStoreException {
        long inserttime = System.currentTimeMillis() / 1000 - 1000000000;
        if (matchBlockTime != null && "".equals(matchBlockTime)) {
            inserttime = Long.parseLong(matchBlockTime);
        }

        return OrderMatchedResponse.createOrderRecordResponse(store.selectOrderRecordMatched(tokenId, inserttime));
    }

    public List<OrderRecordMatched> getOrderRecordMatched(String tokenId, Long matchBlockTime)
            throws BlockStoreException {
        return store.selectOrderRecordMatched(tokenId, matchBlockTime);
    }
}
