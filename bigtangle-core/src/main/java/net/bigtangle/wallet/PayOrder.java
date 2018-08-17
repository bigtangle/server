package net.bigtangle.wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.Json;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.ordermatch.resp.ExchangeInfoResponse;
import net.bigtangle.core.http.server.resp.GetOutputsResponse;
import net.bigtangle.params.OrdermatchReqCmd;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class PayOrder {

    private Wallet wallet;

    private Exchange exchange;

    private String orderid;

    private String marketURL;

    private String serverURL;

    public PayOrder(Wallet wallet, String orderid, String serverURL, String marketURL) throws Exception {
        this.wallet = wallet;
        this.orderid = orderid;
        this.serverURL = serverURL;
        this.marketURL = marketURL;
        this.loadExchangeInfo();
    }

    private void loadExchangeInfo() throws Exception {
        Exchange exchange = this.getExchangeInfoResult(orderid);
        if (exchange == null) {
            throw new RuntimeException("exchange info not found");
        }
        this.exchange = exchange;
    }

    public void sign() throws Exception {
        String dataHex = this.exchange.getDataHex();
        if (dataHex.isEmpty()) {
            this.signOrderTransaction();
        } else {
            this.signOrderComplete();
        }
    }

    private Wallet wallet() {
        return this.wallet;
    }

    private void signOrderComplete() throws Exception {
        String dataHex = this.exchange.getDataHex();
        byte[] buf = Utils.HEX.decode(dataHex);
        ExchangeReload exchangeReload = this.reloadTransaction(buf);
        Transaction transaction = exchangeReload.getTransaction();
        if (transaction == null) {
            return;
        }
        SendRequest request = SendRequest.forTx(transaction);
        this.wallet().setServerURL(this.serverURL);
        this.wallet().signTransaction(request);

        byte[] data = OkHttp3Util.post(this.serverURL + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
        Block rollingBlock = this.wallet().getNetworkParameters().getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();
        OkHttp3Util.post(this.serverURL + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        Exchange exchange = this.getExchangeInfoResult(this.orderid);
        int toSign = exchange.getToSign();
        int fromSign = exchange.getFromSign();
        String toAddress = exchange.getToAddress();
        String fromAddress = exchange.getFromAddress();
        String fromTokenHex = exchange.getFromTokenHex();
        String fromAmount = exchange.getFromAmount();
        String toTokenHex = exchange.getToTokenHex();
        String toAmount = exchange.getToAmount();

        String signtype = "to";
        if (toSign == 0 && this.wallet().calculatedAddressHit(toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.wallet().calculatedAddressHit(fromAddress)) {
            signtype = "from";
        }
        buf = this.makeSignTransactionBuffer(fromAddress, parseCoinValue(fromAmount, fromTokenHex, true), toAddress,
                parseCoinValue(toAmount, toTokenHex, true), transaction.bitcoinSerialize());
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String orderid = stringValueOf(this.orderid);
        requestParam.put("orderid", orderid);
        requestParam.put("dataHex", Utils.HEX.encode(buf));
        requestParam.put("signtype", signtype);
        OkHttp3Util.post(this.marketURL + OrdermatchReqCmd.signTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
    }

    private void signOrderTransaction() throws Exception {
        String fromAddress = stringValueOf(this.exchange.getFromAddress());
        String toAddress = stringValueOf(this.exchange.getToAddress());
        String toTokenHex = stringValueOf(this.exchange.getToTokenHex());
        String fromTokenHex = stringValueOf(this.exchange.getFromTokenHex());
        String toAmount = stringValueOf(this.exchange.getToAmount());
        String fromAmount = stringValueOf(this.exchange.getFromAmount());
        byte[] buf = this.makeSignTransactionBufferCheckSwap(fromAddress,
                parseCoinValue(fromAmount, fromTokenHex, false), toAddress,
                parseCoinValue(toAmount, toTokenHex, false));
        if (buf == null) {
            return;
        }
        int toSign = this.exchange.getToSign();
        int fromSign = this.exchange.getFromSign();
        String signtype = "";
        if (toSign == 0 && this.wallet().calculatedAddressHit(toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.wallet().calculatedAddressHit(fromAddress)) {
            signtype = "from";
        }
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", this.orderid);
        requestParam.put("dataHex", Utils.HEX.encode(buf));
        requestParam.put("signtype", signtype);
        OkHttp3Util.post(this.marketURL + OrdermatchReqCmd.signTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
    }

    private byte[] makeSignTransactionBufferCheckSwap(String fromAddress, Coin fromCoin, String toAddress, Coin toCoin)
            throws Exception {
        String fromAddress00, toAddress00;
        Coin fromCoin00, toCoin00;
        if (this.wallet().calculatedAddressHit(fromAddress)) {
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
        Address fromAddress00 = new Address(this.wallet().getNetworkParameters(), fromAddress);
        Address toAddress00 = new Address(this.wallet().getNetworkParameters(), toAddress);
        byte[] buf = null;
        try {
            List<UTXO> outputs = new ArrayList<UTXO>();
            outputs.addAll(
                    this.getUTXOWithPubKeyHash(toAddress00.getHash160(), Utils.HEX.decode(fromCoin.getTokenHex())));
            outputs.addAll(
                    this.getUTXOWithECKeyList(this.wallet().walletKeys(), Utils.HEX.decode(toCoin.getTokenHex())));

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

            List<TransactionOutput> candidates = this.wallet().transforSpendCandidates(outputs);
            this.wallet().setServerURL(this.serverURL);
            this.wallet().completeTx(req, candidates, false, addressResult);
            this.wallet().signTransaction(req);

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
                + toCoin.toPlainString().getBytes().length + 4 + this.orderid.getBytes().length + 4);

        byteBuffer.putInt(fromAddress.getBytes().length).put(fromAddress.getBytes());
        byteBuffer.putInt(fromCoin.getTokenHex().getBytes().length).put(fromCoin.getTokenHex().getBytes());
        byteBuffer.putInt(fromCoin.toPlainString().getBytes().length).put(fromCoin.toPlainString().getBytes());
        byteBuffer.putInt(toAddress.getBytes().length).put(toAddress.getBytes());
        byteBuffer.putInt(toCoin.getTokenHex().getBytes().length).put(toCoin.getTokenHex().getBytes());
        byteBuffer.putInt(toCoin.toPlainString().getBytes().length).put(toCoin.toPlainString().getBytes());
        byteBuffer.putInt(this.orderid.getBytes().length).put(this.orderid.getBytes());
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

    private Coin parseCoinValue(String toAmount, String toTokenHex, boolean decimal) {
        if (decimal) {
            return Coin.parseCoin(toAmount, Utils.HEX.decode(toTokenHex));
        } else {
            return Coin.valueOf(Long.parseLong(toAmount), Utils.HEX.decode(toTokenHex));
        }
    }

    private List<UTXO> getUTXOWithPubKeyHash(byte[] pubKeyHash, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> pubKeyHashList = new ArrayList<String>();
        pubKeyHashList.add(Utils.HEX.encode(pubKeyHash));
        String response = OkHttp3Util.postString(this.serverURL + ReqCmd.getOutputs.name(),
                Json.jsonmapper().writeValueAsString(pubKeyHashList));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(response, GetOutputsResponse.class);
        for (UTXO utxo : getOutputsResponse.getOutputs()) {
            if (!Arrays.equals(utxo.getTokenidBuf(), tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    private List<UTXO> getUTXOWithECKeyList(List<ECKey> ecKeys, byte[] tokenid) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> pubKeyHashList = new ArrayList<String>();
        for (ECKey ecKey : ecKeys) {
            pubKeyHashList.add(Utils.HEX.encode(ecKey.toAddress(this.wallet().getNetworkParameters()).getHash160()));
        }
        String response = OkHttp3Util.postString(this.serverURL + ReqCmd.getOutputs.name(),
                Json.jsonmapper().writeValueAsString(pubKeyHashList));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(response, GetOutputsResponse.class);
        for (UTXO utxo : getOutputsResponse.getOutputs()) {
            if (!Arrays.equals(utxo.getTokenidBuf(), tokenid)) {
                continue;
            }
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    private ExchangeReload reloadTransaction(byte[] buf) {
        ExchangeReload exchangeReload = new ExchangeReload();
        ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // fromAddressComboBox.setValue(new String(dst));
            exchangeReload.setFromAddress(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // fromTokenHexComboBox.setValue(new String(dst));
            exchangeReload.setFromTokenHex(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // fromAmountTextField.setText(new String(dst));
            exchangeReload.setFromAmount(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // toAddressComboBox.setValue(new String(dst));
            exchangeReload.setToAddress(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // toTokenHexComboBox.setValue(new String(dst));
            exchangeReload.setToTokenHex(new String(dst));
        }
        {
            byte[] dst = new byte[byteBuffer.getInt()];
            byteBuffer.get(dst);
            // toAmountTextField.setText(new String(dst));
            exchangeReload.setToAmount(new String(dst));
        }
        byte[] orderid = new byte[byteBuffer.getInt()];
        byteBuffer.get(orderid);

        this.orderid = new String(orderid);
        // System.out.println("orderid : " + new String(orderid));
        exchangeReload.setOrderid(this.orderid);

        int len = byteBuffer.getInt();
        System.out.println("tx len : " + len);
        byte[] data = new byte[len];
        byteBuffer.get(data);
        try {
            Transaction transaction = (Transaction) this.wallet().getNetworkParameters().getDefaultSerializer()
                    .makeTransaction(data);
            exchangeReload.setTransaction(transaction);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return exchangeReload;
    }

    private Exchange getExchangeInfoResult(String orderid) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String respone = OkHttp3Util.postString(this.marketURL + OrdermatchReqCmd.exchangeInfo.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        ExchangeInfoResponse exchangeInfoResponse = Json.jsonmapper().readValue(respone, ExchangeInfoResponse.class);
        return exchangeInfoResponse.getExchange();
    }
}
