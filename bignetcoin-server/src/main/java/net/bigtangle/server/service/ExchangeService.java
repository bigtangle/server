package net.bigtangle.server.service;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.Utils;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetExchangeResponse;
import net.bigtangle.store.FullPrunedBlockStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        String signtype = (String) request.get("signtype");
        byte[] data = Utils.HEX.decode(dataHex);
        this.store.updateExchangeSign(orderid, signtype, data);
        
        Exchange exchange = this.store.getExchangeInfoByOrderid(orderid);
        return AbstractResponse.createEmptyResponse();
    }

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getExchangeListWithAddress(String address) throws BlockStoreException {
        List<Exchange> list = this.store.getExchangeListWithAddress(address);
        return GetExchangeResponse.create(list);
    }
}
