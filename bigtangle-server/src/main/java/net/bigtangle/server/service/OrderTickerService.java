package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.stereotype.Service;

import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.ordermatch.MatchLastdayResult;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.core.response.OrderTickerResponse;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.store.FullBlockStore;

/**
 * This service provides informations on current exchange rates
 */
@Service
public class OrderTickerService {

    private int MAXCOUNT = 500;

    


    /**
     * Returns a list of up to n last prices in ascending occurrence
     * 
     * @param tokenId
     *            ID of token
     * @return a list of up to n last prices in ascending occurrence
     * @throws BlockStoreException
     */
    public OrderTickerResponse getLastMatchingEvents(Set<String> tokenIds, String basetoken, FullBlockStore store)
            throws BlockStoreException {
        Set<String> basetokens = new HashSet<String>();
        basetokens.add(basetoken);
        List<MatchLastdayResult> re = store.getLastMatchingEvents(tokenIds, basetoken);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokenameA(re, store));

    }

 
    public Map<String, Token> getTokenameA(List<MatchLastdayResult> res, FullBlockStore store) throws BlockStoreException {
        Set<String> tokenids = new HashSet<String>();
        for (MatchLastdayResult d : res) {
            tokenids.add(d.getTokenid());
            tokenids.add(d.getBasetokenid());
        }
        Map<String, Token> re = new HashMap<String, Token>();
        List<Token> tokens = store.getTokensList(tokenids);
        for (Token t : tokens) {
            re.put(t.getTokenid(), t);
        }
        return re;
    }


    // @Cacheable("priceticker")
    public OrderTickerResponse getTimeBetweenMatchingEvents(Set<String> tokenids, String basetoken, Long startDate,
            Long endDate, FullBlockStore store) throws BlockStoreException {
        Iterator<String> iter = tokenids.iterator();
        String first = iter.next();
        List<MatchLastdayResult> re = store.getTimeBetweenMatchingEvents(first, basetoken, startDate, endDate, MAXCOUNT);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokenameA(re, store));
    }

    public OrderTickerResponse getTimeAVBGBetweenMatchingEvents(Set<String> tokenids, String basetoken, Long startDate,
            Long endDate, FullBlockStore store) throws BlockStoreException {
        Iterator<String> iter = tokenids.iterator();
        String first = iter.next();
        List<MatchLastdayResult> re = store.getTimeAVGBetweenMatchingEvents(first, basetoken, startDate, endDate, MAXCOUNT);
        return OrderTickerResponse.createOrderRecordResponse(re, getTokenameA(re, store));
    }

}
