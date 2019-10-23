package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class OrderdataService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderdataList(boolean spent, String address, List<String> addresses, String tokenid)
            throws BlockStoreException {
        if (addresses == null)
            addresses = new ArrayList<String>();
        if (address != null && !"".equals(address)) {
            addresses.add(address);
        }
        if (!spent) {
            return getAllOpenOrders(addresses, tokenid);
        } else {
            // only my closed orders
            return getMyClosedOrders(addresses);
        }
    }

    private AbstractResponse getAllOpenOrders(List<String> addresses, String tokenid) throws BlockStoreException {
        List<OrderRecord> allOrdersSorted = store.getAllOpenOrdersSorted(addresses, tokenid);

        HashSet<String> orderBlockHashs = new HashSet<String>();
        for (OrderRecord orderRecord : allOrdersSorted) {
            orderBlockHashs.add(orderRecord.getBlockHashHex());
        }

        List<OrderCancel> orderCancels = this.store.getOrderCancelByOrderBlockHash(orderBlockHashs);
        HashMap<String, OrderCancel> orderCannelData = new HashMap<String, OrderCancel>();
        for (OrderCancel orderCancel : orderCancels) {
            orderCannelData.put(orderCancel.getOrderBlockHash().toString(), orderCancel);
        }

        for (OrderRecord orderRecord : allOrdersSorted) {
            if (orderCannelData.containsKey(orderRecord.getBlockHashHex())) {
                orderRecord.setCancelPending(true);
            } else {
                orderRecord.setCancelPending(false);
            }
        }

        return OrderdataResponse.createOrderRecordResponse(allOrdersSorted, getTokename(allOrdersSorted));
    }

    private AbstractResponse getMyClosedOrders(List<String> addresses) throws BlockStoreException {
        List<OrderRecord> allOrdersSorted = store.getMyClosedOrders(addresses);

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
