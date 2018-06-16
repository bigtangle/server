package net.bigtangle.tools.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class PayOrder {
    
    private Account account;
    
    private HashMap<String, Object> exchangeResult;
    
    private String mOrderid;
    
    public PayOrder(Account account, HashMap<String, Object> exchangeResult) {
        this.account = account;
        this.exchangeResult = exchangeResult;
        this.mOrderid = stringValueOf(exchangeResult.get("orderid"));
    }

    public void signOrderComplete() throws Exception {
        String dataHex = (String) exchangeResult.get("dataHex");
        byte[] buf = Utils.HEX.decode(dataHex);
        Transaction transaction = this.reloadTransaction(buf);
        if (transaction == null) {
            return;
        }
        SendRequest request = SendRequest.forTx(transaction);
        this.account.wallet().signTransaction(request);

        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "askTransaction", Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
        Block rollingBlock = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();
        OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());

        HashMap<String, Object> exchangeResult = this.getExchangeInfoResult(this.mOrderid);
        int toSign = (int) exchangeResult.get("toSign");
        int fromSign = (int) exchangeResult.get("fromSign");
        String toAddress = (String) exchangeResult.get("toAddress");
        String fromAddress = (String) exchangeResult.get("fromAddress");
        String fromTokenHex = (String) exchangeResult.get("fromTokenHex");
        String fromAmount = (String) exchangeResult.get("fromAmount");
        String toTokenHex = (String) exchangeResult.get("toTokenHex");
        String toAmount = (String) exchangeResult.get("toAmount");

        String signtype = "";
        if (toSign == 0 && this.account.calculatedAddressHit(toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.account.calculatedAddressHit(fromAddress)) {
            signtype = "from";
        }
        buf = this.makeSignTransactionBuffer(fromAddress, parseCoinValue(fromAmount, fromTokenHex, true), toAddress, parseCoinValue(toAmount, toTokenHex, true), transaction.bitcoinSerialize());
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String orderid = stringValueOf(mOrderid);
        requestParam.put("orderid", orderid);
        requestParam.put("dataHex", Utils.HEX.encode(buf));
        requestParam.put("signtype", signtype);
        OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam));
    }

    public void signOrderTransaction() throws Exception {
        String fromAddress = stringValueOf(exchangeResult.get("fromAddress"));
        String toAddress = stringValueOf(exchangeResult.get("toAddress"));
        String toTokenHex = stringValueOf(exchangeResult.get("toTokenHex"));
        String fromTokenHex = stringValueOf(exchangeResult.get("fromTokenHex"));
        String toAmount = stringValueOf(exchangeResult.get("toAmount"));
        String fromAmount = stringValueOf(exchangeResult.get("fromAmount"));
        byte[] buf = this.makeSignTransactionBufferCheckSwap(fromAddress, parseCoinValue(fromAmount, fromTokenHex, false), toAddress, parseCoinValue(toAmount, toTokenHex, false));
        if (buf == null) {
            return;
        }
        int toSign = (int) exchangeResult.get("toSign");
        int fromSign = (int) exchangeResult.get("fromSign");
        String signtype = "";
        if (toSign == 0 && this.account.calculatedAddressHit(toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.account.calculatedAddressHit(fromAddress)) {
            signtype = "from";
        }
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", this.mOrderid);
        requestParam.put("dataHex", Utils.HEX.encode(buf));
        requestParam.put("signtype", signtype);
        OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        return;
    }
    

    private byte[] makeSignTransactionBufferCheckSwap(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin) throws Exception {
        String fromAddress00, toAddress00;
        Coin fromCoin00, toCoin00;
        if (this.account.calculatedAddressHit(fromAddress)) {
            fromAddress00 = fromAddress;
            toAddress00 = toAddress;
            fromCoin00 = fromCoin;
            toCoin00 = toCoin;
        } else {
            fromAddress00 = toAddress;
            toAddress00 = fromAddress;
            fromCoin00 = toCoin;
            toCoin00 = fromCoin;
        }
        return makeSignTransactionBuffer(fromAddress00, fromCoin00, toAddress00, toCoin00);
    }

    @SuppressWarnings("deprecation")
    private byte[] makeSignTransactionBuffer(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin) {
        Address fromAddress00 = new Address(Configure.PARAMS, fromAddress);
        Address toAddress00 = new Address(Configure.PARAMS, toAddress);
        byte[] buf = null;
        try {
            List<UTXO> outputs = new ArrayList<UTXO>();
            outputs.addAll(this.getUTXOWithPubKeyHash(toAddress00.getHash160(), Utils.HEX.decode(fromCoin.getTokenHex())));
            outputs.addAll(this.getUTXOWithECKeyList(this.account.walletKeys(), Utils.HEX.decode(toCoin.getTokenHex())));

            SendRequest req = SendRequest.to(toAddress00, toCoin);
            req.tx.addOutput(fromCoin, fromAddress00);

            // SendRequest req = SendRequest.to(fromAddress00,fromAmount );
            // req.tx.addOutput(toAmount , toAddress00 );

            req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;

            HashMap<String, Address> addressResult = new HashMap<String, Address>();
            addressResult.put(fromCoin.getTokenHex(), toAddress00);
            addressResult.put(toCoin.getTokenHex(), fromAddress00);

            // addressResult.put((String) exchangemap.get("fromTokenHex"),
            // toAddress00);
            // addressResult.put((String) exchangemap.get("toTokenHex"),
            // fromAddress00);

            List<TransactionOutput> candidates = this.account.wallet().transforSpendCandidates(outputs);
            this.account.wallet().setServerURL(Configure.SIMPLE_SERVER_CONTEXT_ROOT);
            this.account.wallet().completeTx(req, candidates, false, addressResult);
            this.account.wallet().signTransaction(req);

            // walletAppKit.wallet().completeTx(req,
            // walletAppKit.wallet().transforSpendCandidates(ulist), false,
            // addressResult);
            // walletAppKit.wallet().signTransaction(req);
            buf = req.tx.bitcoinSerialize();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return makeSignTransactionBuffer(fromAddress, fromCoin, toAddress, toCoin, buf);
    }
    
    private byte[] makeSignTransactionBuffer(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin,
            byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(buf.length + 4 + fromAddress.getBytes().length + 4
                + fromCoin.getTokenHex().getBytes().length + 4 + fromCoin.toPlainString().getBytes().length + 4
                + toAddress.getBytes().length + 4 + toCoin.getTokenHex().getBytes().length + 4
                + toCoin.toPlainString().getBytes().length + 4 + this.mOrderid.getBytes().length + 4);

        byteBuffer.putInt(fromAddress.getBytes().length).put(fromAddress.getBytes());
        byteBuffer.putInt(fromCoin.getTokenHex().getBytes().length).put(fromCoin.getTokenHex().getBytes());
        byteBuffer.putInt(fromCoin.toPlainString().getBytes().length).put(fromCoin.toPlainString().getBytes());
        byteBuffer.putInt(toAddress.getBytes().length).put(toAddress.getBytes());
        byteBuffer.putInt(toCoin.getTokenHex().getBytes().length).put(toCoin.getTokenHex().getBytes());
        byteBuffer.putInt(toCoin.toPlainString().getBytes().length).put(toCoin.toPlainString().getBytes());
        byteBuffer.putInt(this.mOrderid.getBytes().length).put(this.mOrderid.getBytes());
        byteBuffer.putInt(buf.length).put(buf);
        // System.out.println("tx len : " + buf.length);
        return byteBuffer.array();
    }

    private static String stringValueOf(Object object) {
        if (object == null) {
            return "";
        } else {
            return String.valueOf(object);
        }
    }
    
    public Coin parseCoinValue(String toAmount, String toTokenHex, boolean decimal) {
        if (decimal) {
            return Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
        } else {
            return Coin.valueOf(Long.parseLong(toAmount), Utils.HEX.decode(toTokenHex));
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<UTXO> getUTXOWithPubKeyHash(byte[] pubKeyHash, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> pubKeyHashList = new ArrayList<String>();
        pubKeyHashList.add(Utils.HEX.encode(pubKeyHash));
        String response = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "getOutputs", Json.jsonmapper().writeValueAsString(pubKeyHashList));
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data == null || data.isEmpty()) {
            return listUTXO;
        }
        List<Map<String, Object>> outputs = (List<Map<String, Object>>) data.get("outputs");
        if (outputs == null || outputs.isEmpty()) {
            return listUTXO;
        }
        for (Map<String, Object> object : outputs) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(object);
            if (!Arrays.equals(utxo.getTokenidBuf(), tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }
    
    @SuppressWarnings("unchecked")
    public List<UTXO> getUTXOWithECKeyList(List<ECKey> ecKeys, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        for (ECKey ecKey : ecKeys) {
            List<String> pubKeyHashList = new ArrayList<String>();
            pubKeyHashList.add(Utils.HEX.encode(ecKey.toAddress(Configure.PARAMS).getHash160()));
            String response = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "getOutputs", Json.jsonmapper().writeValueAsString(pubKeyHashList));
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            if (data == null || data.isEmpty()) {
                return listUTXO;
            }
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) data.get("outputs");
            if (outputs == null || outputs.isEmpty()) {
                return listUTXO;
            }
            for (Map<String, Object> object : outputs) {
                UTXO utxo = MapToBeanMapperUtil.parseUTXO(object);
                if (!Arrays.equals(utxo.getTokenidBuf(), tokenid)) {
                    continue;
                }
                if (utxo.getValue().getValue() > 0) {
                    listUTXO.add(utxo);
                }
            }
        }
        return listUTXO;
    }
    

    private Transaction reloadTransaction(byte[] buf) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            fromAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            fromTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            fromAmountTextField.setText(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            toAddressComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            toTokenHexComboBox.setValue(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
//            toAmountTextField.setText(new String(dst));
        }
        byte[] orderid = new byte[byteBuffer.getInt()];
        byteBuffer.get(orderid);

        mOrderid = new String(orderid);
        // System.out.println("orderid : " + new String(orderid));

        int len = byteBuffer.getInt();
        // System.out.println("tx len : " + len);
        byte[] data = new byte[len];
        byteBuffer.get(data);
        try {
            return (Transaction) Configure.PARAMS.getDefaultSerializer().makeTransaction(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public HashMap<String, Object> getExchangeInfoResult(String orderid) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String respone = OkHttp3Util.postString(Configure.ORDER_MATCH_CONTEXT_ROOT + "exchangeInfo",
                Json.jsonmapper().writeValueAsString(requestParam));
        HashMap<String, Object> result = Json.jsonmapper().readValue(respone, HashMap.class);
        HashMap<String, Object> exchange = (HashMap<String, Object>) result.get("exchange");
        return exchange;
    }
}
