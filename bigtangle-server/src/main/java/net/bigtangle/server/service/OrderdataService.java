package net.bigtangle.server.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class OrderdataService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderdataList(boolean spent) throws BlockStoreException {

        List<OrderRecord> allOrdersSorted = store.getAllAvailableOrdersSorted(spent);
        return OrderdataResponse.createOrderRecordResponse(allOrdersSorted, getTokename(allOrdersSorted));
    }

    public AbstractResponse getOrderdataList(boolean spent, String address, List<String> addresses)
            throws BlockStoreException {

        List<OrderRecord> allOrdersSorted = store.getAllAvailableOrdersSorted(spent, address, addresses);

        // List<OrderRecord> closedOrders = store.getMyClosedOrders(address);
        // TODO closed with part match (initialOrders - remainingOrders
        // )+closedOrders
        // List<OrderRecord> initialOrders =
        // store.getMyInitialOpenOrders(address);
        // List<OrderRecord> remainingOrders =
        // store.getMyRemainingOpenOrders(address);
        // if (spent)
        // return OrderdataResponse.createOrderRecordResponse(closedOrders);
        // else
        return OrderdataResponse.createOrderRecordResponse(allOrdersSorted, getTokename(allOrdersSorted));
    }

    public Map<String, Token> getTokename(List<OrderRecord> allOrdersSorted) throws BlockStoreException {
        Set<String> tokenids = new HashSet<String>();
        for (OrderRecord d : allOrdersSorted) {
            tokenids.add(d.getOfferTokenid());
            tokenids.add(d.getTargetTokenid());
        }
        Map<String, Token> re = new HashMap<String, Token>();
        List<Token> tokens = store.getTokensList(tokenids);
        for (Token t : tokens) {
            re.put(t.getTokenid(), t);
        }
        return re;
    }

}
