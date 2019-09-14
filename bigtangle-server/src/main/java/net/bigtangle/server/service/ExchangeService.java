/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Exchange;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.ordermatch.resp.ExchangeInfoResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class ExchangeService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getExchangeByOrderid(String orderid) throws BlockStoreException {
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        return ExchangeInfoResponse.create(exchange);
    }

    public AbstractResponse saveExchange(Map<String, Object> request) throws BlockStoreException {
        String orderid = (String) request.get("orderid");
        String fromAddress = (String) request.get("fromAddress");
        String fromTokenHex = (String) request.get("fromTokenHex");
        String fromAmount = (String) request.get("fromAmount");
        String toAddress = (String) request.get("toAddress");
        String toTokenHex = (String) request.get("toTokenHex");
        String toAmount = (String) request.get("toAmount");
        String dataHex = (String) request.get("dataHex");
        byte[] data = new byte[0];
        Exchange exchange = new Exchange(fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, data);
        exchange.setOrderid(orderid);
        exchange.setFromSign(1);
        this.store.saveExchange(exchange);
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