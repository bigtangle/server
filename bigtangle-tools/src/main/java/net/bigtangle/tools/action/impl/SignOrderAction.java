package net.bigtangle.tools.action.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class SignOrderAction extends Action {

    public SignOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute0() throws Exception {
        for (ECKey key : this.account.walletKeys()) {
            String address = key.toAddress(Configure.PARAMS).toString();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("address", address);
            String response = OkHttp3Util.post(Configure.CONTEXT_ROOT + "getExchange", Json.jsonmapper().writeValueAsString(requestParam).getBytes());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            if (data == null) {
                continue;
            }
            List<Map<String, Object>> exchanges = (List<Map<String, Object>>) data.get("exchanges");
            if (exchanges == null || exchanges.isEmpty()) {
                continue;
            }
            for (Map<String, Object> result : exchanges) {
                if ((Integer) result.get("toSign") + (Integer) result.get("fromSign") == 2) {
                    continue;
                }
                String orderid = (String) result.get("orderid");
                HashMap<String, Object> exchangeResult = this.getExchangeInfoResult(orderid);
                if (exchangeResult == null) {
                    continue;
                }
                String dataHex = (String) exchangeResult.get("dataHex");
                if (dataHex.isEmpty()) {
                    continue;
                }
                String toAddress = (String) exchangeResult.get("toAddress");
                String fromAddress = (String) exchangeResult.get("fromAddress");
                int toSign = (Integer) exchangeResult.get("toSign");
                int fromSign = (Integer) exchangeResult.get("fromSign");
                
                String typeStr = "";
                if (toSign == 0 && this.account.calculatedAddressHit(toAddress)) {
                    typeStr = "to";
                } else if (fromSign == 0 && this.account.calculatedAddressHit(fromAddress)) {
                    typeStr = "from";
                }
                
                if (typeStr.equals("")) {
                    continue;
                }
                String fromTokenHex = (String) exchangeResult.get("fromTokenHex");
                String fromAmount = (String) exchangeResult.get("fromAmount");
                String toTokenHex = (String) exchangeResult.get("toTokenHex");
                String toAmount = (String) exchangeResult.get("toAmount");
                
//                byte[] buf = this.makeSignTransactionBuffer(fromAddress, this.parseCoin(fromAmount, fromTokenHex), toAddress,
//                        this.parseCoin(toAmount, toTokenHex), mTransaction.bitcoinSerialize());
                
//                HashMap<String, Object> requestParam0000 = new HashMap<String, Object>();
//                requestParam0000.put("orderid", orderid);
//                requestParam0000.put("dataHex", Utils.HEX.encode(buf));
//                requestParam0000.put("signtype", typeStr);
//                OkHttp3Util.post(Configure.CONTEXT_ROOT + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam0000));
            }
        }
    }
    
    private Coin parseCoin(String amount, String tokenHex) {
        return Coin.parseCoin(amount, Utils.HEX.decode(tokenHex));
    }

    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getExchangeInfoResult(String orderid) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String respone = OkHttp3Util.postString(Configure.CONTEXT_ROOT + "exchangeInfo", Json.jsonmapper().writeValueAsString(requestParam));
        HashMap<String, Object> result = Json.jsonmapper().readValue(respone, HashMap.class);
        HashMap<String, Object> exchange = (HashMap<String, Object>) result.get("exchange");
        return exchange;
    }

}
