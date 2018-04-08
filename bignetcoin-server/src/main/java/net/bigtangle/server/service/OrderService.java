package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import net.bigtangle.core.Order;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetOrderResponse;

@Service
public class OrderService {

    public AbstractResponse saveOrder(Map<String, Object> request) {
        return AbstractResponse.createEmptyResponse();
    }

    public AbstractResponse getOrderList() {
        List<Order> orders = new ArrayList<Order>();
        return GetOrderResponse.create(orders);
    }
}
