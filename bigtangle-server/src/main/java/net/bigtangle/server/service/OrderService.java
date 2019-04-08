package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.store.FullPrunedBlockStore;

/**
 * This service provides informations on current open orders
 */
@Service
public class OrderService {

    @Autowired
    protected FullPrunedBlockStore store;
    
    
    
    public OrderService() {
        // TODO cache order books
        // TODO keep order books persistent in db
    }
}
