package net.bigtangle.server.service;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Order;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetOrderResponse;
import net.bigtangle.store.FullPrunedBlockStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public AbstractResponse saveOrder(Map<String, Object> request) throws Exception {
        String address = (String) request.get("address");
        String tokenid = (String) request.get("tokenid");
        int type = (Integer) request.get("type");
        String validateto = (String) request.get("validateto");
        String validatefrom = (String) request.get("validatefrom");
        int limitl = (Integer) request.get("limitl");
        
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Order order = Order.create(address, tokenid, type, 
                simpleDateFormat.parse(validateto), simpleDateFormat.parse(validatefrom), limitl);
        store.saveOrder(order);
        return AbstractResponse.createEmptyResponse();
    }
    
    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderList() throws BlockStoreException {
        List<Order> orders = this.store.getOrderList();
        return GetOrderResponse.create(orders);
    }
}
