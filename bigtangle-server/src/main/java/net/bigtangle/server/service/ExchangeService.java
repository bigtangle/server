/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Exchange;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.ExchangeInfoResponse;
import net.bigtangle.core.response.GetExchangeResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class ExchangeService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getExchangeByOrderid(String orderid) throws BlockStoreException {
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        return ExchangeInfoResponse.create(exchange);
    }

    public AbstractResponse getBatchExchangeListByAddressListA(List<String> address) throws BlockStoreException {
        List<Exchange> list = new ArrayList<Exchange>();
        for (String s : address) {
            list.addAll(this.store.getExchangeListWithAddressA(s));
        }
        return GetExchangeResponse.create(list);
    }

    public AbstractResponse saveExchange(Map<String, Object> request) throws BlockStoreException {
        String orderid = (String) request.get("orderid");
        String fromAddress = (String) request.get("fromAddress");
        String fromTokenHex = (String) request.get("fromTokenHex");
        String fromAmount = (String) request.get("fromAmount");
        String toAddress = (String) request.get("toAddress");
        String toTokenHex = (String) request.get("toTokenHex");
        String toAmount = (String) request.get("toAmount");
        // String dataHex = (String) request.get("dataHex");
        // byte[] data = Utils.HEX.decode(dataHex);
        Exchange exchange = new Exchange(fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount,
                new byte[0]);
        exchange.setOrderid(orderid);
        exchange.setFromSign(1);
        exchange.setMemo((String) request.get("memo"));
        this.store.saveExchange(exchange);
        return AbstractResponse.createEmptyResponse();
    }

    public AbstractResponse deleteExchange(Map<String, Object> request) throws BlockStoreException {
        String orderid = (String) request.get("orderid");
        this.store.deleteExchange(orderid);
        return AbstractResponse.createEmptyResponse();
    }

    public AbstractResponse signMultiTransaction(Map<String, Object> request) throws BlockStoreException {
        String dataHex = (String) request.get("dataHex");
        String signInputDataHex = (String) request.get("signInputDataHex");
        String orderid = (String) request.get("orderid");

        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        if (signInputDataHex != null && !signInputDataHex.isEmpty()) {
            this.store.updateExchangeSignData(orderid, Utils.HEX.decode(signInputDataHex));
        }
        String signtype = (String) request.get("signtype");
        byte[] data = Utils.HEX.decode(dataHex);
        this.store.updateExchangeSign(orderid, signtype, data);
        exchange = this.store.getExchangeInfoByOrderid(orderid);
        if (exchange.getToSign() == 1 && exchange.getFromSign() == 1 && StringUtils.isNotBlank(exchange.getToOrderId())
                && StringUtils.isNotBlank(exchange.getFromOrderId())) {
        }
        return AbstractResponse.createEmptyResponse();
    }

    public AbstractResponse signTransaction(Map<String, Object> request) throws BlockStoreException {
        String dataHex = (String) request.get("dataHex");
        String orderid = (String) request.get("orderid");

        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);

        String signtype = (String) request.get("signtype");
        byte[] data = Utils.HEX.decode(dataHex);
        this.store.updateExchangeSign(orderid, signtype, data);
        exchange = this.store.getExchangeInfoByOrderid(orderid);
        if (exchange.getToSign() == 1 && exchange.getFromSign() == 1 && StringUtils.isNotBlank(exchange.getToOrderId())
                && StringUtils.isNotBlank(exchange.getFromOrderId())) {
        }
        return AbstractResponse.createEmptyResponse();
    }
}
