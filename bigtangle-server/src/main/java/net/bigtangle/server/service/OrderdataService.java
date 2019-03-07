package net.bigtangle.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class OrderdataService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderdataList() throws BlockStoreException {

        List<OrderRecord> allOrdersSorted = store.getAllAvailableOrdersSorted();
        return OrderdataResponse.createOrderRecordResponse(allOrdersSorted);
    }
}
