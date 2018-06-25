/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ordermatch.service.response.AbstractResponse;
import net.bigtangle.server.ordermatch.service.response.ExchangeInfoResponse;
import net.bigtangle.server.ordermatch.service.response.GetExchangeResponse;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;
import net.bigtangle.utils.OrderState;

@Service
public class ExchangeService {

    public AbstractResponse saveExchange(Map<String, Object> request) throws BlockStoreException {
        String orderid = (String) request.get("orderid");
        String fromAddress = (String) request.get("fromAddress");
        String fromTokenHex = (String) request.get("fromTokenHex");
        String fromAmount = (String) request.get("fromAmount");
        String toAddress = (String) request.get("toAddress");
        String toTokenHex = (String) request.get("toTokenHex");
        String toAmount = (String) request.get("toAmount");
        String dataHex = (String) request.get("dataHex");
        byte[] data = Utils.HEX.decode(dataHex);
        Exchange exchange = new Exchange(fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, data);
        exchange.setOrderid(orderid);
        exchange.setFromSign(1);
        this.store.saveExchange(exchange);
        return AbstractResponse.createEmptyResponse();
    }
    
    public AbstractResponse signTransaction(Map<String, Object> request) throws BlockStoreException {
        String dataHex = (String) request.get("dataHex");
        String orderid = (String) request.get("orderid");
        
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        OrderPublish orderPublish1 = this.store.getOrderPublishByOrderid(exchange.getToOrderId());
        OrderPublish orderPublish2 = this.store.getOrderPublishByOrderid(exchange.getFromOrderId());
        
        if (orderPublish1.getState() == OrderState.finish.ordinal() 
                || orderPublish2.getState() == OrderState.finish.ordinal()) {
            throw new BlockStoreException("order publish state finish");
        }
        
        String signtype = (String) request.get("signtype");
        byte[] data = Utils.HEX.decode(dataHex);
        this.store.updateExchangeSign(orderid, signtype, data);
        
        
        if (exchange.getToSign() == 1 && exchange.getFromSign() == 1 && StringUtils.isNotBlank(exchange.getToOrderId())
                && StringUtils.isNotBlank(exchange.getFromOrderId())) {
            this.store.updateOrderPublishState(exchange.getToOrderId(), OrderState.finish.ordinal());
            this.store.updateOrderPublishState(exchange.getFromOrderId(), OrderState.finish.ordinal());
        }
        return AbstractResponse.createEmptyResponse();
    }

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getExchangeListWithAddress(String address) throws BlockStoreException {
        List<Exchange> list = this.store.getExchangeListWithAddress(address);
        return GetExchangeResponse.create(list);
    }

    public AbstractResponse getExchangeByOrderid(String orderid) throws BlockStoreException {
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        return ExchangeInfoResponse.create(exchange);
    }

    public void cancelOrderSign(String orderid) throws BlockStoreException {
        // TODO check sign
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        this.store.updateOrderPublishState(exchange.getToOrderId(), OrderState.finish.ordinal());
        this.store.updateOrderPublishState(exchange.getFromOrderId(), OrderState.finish.ordinal());
    }

    public AbstractResponse getBatchExchangeListByAddressList(List<String> address) throws BlockStoreException {
        List<Exchange> list = new ArrayList<Exchange>();
        for (String s : address) {
            list.addAll(this.store.getExchangeListWithAddress(s));
        }
        return GetExchangeResponse.create(list);
    }
}
