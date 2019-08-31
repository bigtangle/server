/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.ExchangeMulti;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.ordermatch.resp.ExchangeInfoResponse;
import net.bigtangle.core.http.server.resp.GetOutputsResponse;
import net.bigtangle.core.http.server.resp.OutputsDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignAddressListResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.OrdermatchReqCmd;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class PayOTCOrder {

    private Wallet wallet;

    private Exchange exchange;

    private String orderid;

    private String marketURL;

    private String serverURL;
    private KeyParameter aesKey = null;
    private boolean sellFlag;

    public PayOTCOrder(Wallet wallet, String orderid, String serverURL, String marketURL) throws Exception {
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
        List<ECKey> ecKeys = wallet.walletKeys(aesKey);

        List<ExchangeMulti> exchangeMultis = this.exchange.getExchangeMultis();
        if (exchangeMultis != null && !exchangeMultis.isEmpty()) {
            int flag = 0;// 0:buy,1:sell,2:sign
            List<String> myaddresses = new ArrayList<String>();
            ECKey signEckey = null;
            for (ECKey key : ecKeys) {
                myaddresses.add(key.toAddress(wallet.params).toString());
                if (sellFlag) {
                    if (this.exchange.getToAddress().equals(key.toAddress(wallet.params).toString())) {
                        signEckey = key;
                    }
                }

            }
            if (myaddresses.contains(this.exchange.getToAddress())) {
                flag = 1;
            }

            int size = exchangeMultis.size();
            int signCount = 0;
            int fromsign = this.exchange.getFromSign();
            int tosign = this.exchange.getToSign();
            int othersign = 0;
            for (ExchangeMulti exchangeMulti : exchangeMultis) {
                if (myaddresses.contains(exchangeMulti.getPubkey())) {
                    flag = 2;
                    if (exchangeMulti.getSign() == 1) {
                        othersign = 1;
                    }
                    break;
                }
            }

            for (ExchangeMulti exchangeMulti : exchangeMultis) {
                if (exchangeMulti.getSign() == 1) {
                    signCount++;
                }
            }

            if (flag == 0) {
                if (fromsign == 0 && this.exchange.getSigs().isEmpty()) {

                }
            }
            if (flag == 1) {
                if (tosign == 0) {
                    String dataHex = this.exchange.getDataHex();
                    if (!dataHex.isEmpty()) {
                        List<UTXO> utxos = this.getUTXOWithECKeyList(ecKeys,
                                Utils.HEX.decode(this.exchange.getFromTokenHex()));

                        for (UTXO utxo : utxos) {
                            if (utxo.getTokenId().equals(this.exchange.getFromTokenHex()) && utxo.getMinimumsign() >= 2
                                    && utxo.getValue().getValue() >= Long.parseLong(this.exchange.getFromAmount())) {
                                TransactionOutput multisigOutput_ = new FreeStandingTransactionOutput(wallet.params,
                                        utxo);
                                Script multisigScript_ = multisigOutput_.getScriptPubKey();

                                byte[] buf = Utils.HEX.decode(dataHex);
                                OTCReload exchangeReload = this.reloadTransaction(buf);
                                Transaction transaction0 = exchangeReload.getTransaction();
                                if (transaction0 == null) {
                                    return;
                                }
                                Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript_,
                                        Transaction.SigHash.ALL, false);
                                TransactionSignature transactionSignature = new TransactionSignature(
                                        signEckey.sign(sighash, aesKey), Transaction.SigHash.ALL, false);

                                ECKey.ECDSASignature party1Signature = signEckey.sign(transaction0.getHash(), aesKey);
                                byte[] signature = party1Signature.encodeToDER();
                                boolean success = ECKey.verify(transaction0.getHash().getBytes(), signature,
                                        signEckey.getPubKey());
                                if (!success) {
                                    throw new BlockStoreException("multisign signature error");
                                }

                            }

                        }

                    }
                }
            }
            if (flag == 2) {

            }

        } else {
            String dataHex = this.exchange.getDataHex();
            if (dataHex.isEmpty()) {
                this.signOrderTransaction();
            } else {
                this.signOrderComplete();
            }
        }

    }

    private Wallet wallet() {
        return this.wallet;
    }

    private void signOrderComplete() throws Exception {
        String dataHex = this.exchange.getDataHex();
        byte[] buf = Utils.HEX.decode(dataHex);
        OTCReload exchangeReload = this.reloadTransaction(buf);
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
        if (toSign == 0 && this.wallet().calculatedAddressHit(aesKey, toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.wallet().calculatedAddressHit(aesKey,fromAddress)) {
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
        if (toSign == 0 && this.wallet().calculatedAddressHit(aesKey,toAddress)) {
            signtype = "to";
        } else if (fromSign == 0 && this.wallet().calculatedAddressHit(aesKey,fromAddress)) {
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
        if (this.wallet().calculatedAddressHit(aesKey,fromAddress)) {
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
            outputs.addAll(this.getUTXOWithECKeyList(this.wallet().walletKeys(aesKey),
                    Utils.HEX.decode(toCoin.getTokenHex())));

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
        
            return MonetaryFormat.FIAT.noCode().parse(toAmount, Utils.HEX.decode(toTokenHex));
         
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

    private OTCReload reloadTransaction(byte[] buf) {
        OTCReload exchangeReload = new OTCReload();
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

    private void exchangeSignInit(String orderid) throws Exception {
        String ContextRoot = serverURL;
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", orderid);
        String resp = OkHttp3Util.postString(ContextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignAddressListResponse.class);
        List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse.getPayMultiSignAddresses();

        
        ECKey currentECKey = null;

        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
            if (payMultiSignAddress.getSign() == 1) {
                continue;
            }
            for (ECKey ecKey : wallet.walletKeys(aesKey)) {
                if (ecKey.getPublicKeyAsHex().equals(payMultiSignAddress.getPubKey())) {
                    currentECKey = ecKey;
                    break;
                }
            }
        }
        if (currentECKey == null) {
            return;
        }
        exchangeSign(currentECKey, orderid, wallet.params, ContextRoot);
    }

    private void exchangeSign(ECKey ecKey, String orderid, NetworkParameters networkParameters, String contextRoot)
            throws Exception {
        List<String> pubKeys = new ArrayList<String>();
        pubKeys.add(ecKey.getPublicKeyAsHex());

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.clear();
        requestParam.put("orderid", orderid);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSignDetails.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignDetailsResponse payMultiSignDetailsResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignDetailsResponse.class);
        PayMultiSign payMultiSign_ = payMultiSignDetailsResponse.getPayMultiSign();

        requestParam.clear();
        requestParam.put("hexStr", payMultiSign_.getOutputHashHex());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        OutputsDetailsResponse outputsDetailsResponse = Json.jsonmapper().readValue(resp, OutputsDetailsResponse.class);
        UTXO u = outputsDetailsResponse.getOutputs();

        TransactionOutput multisigOutput_ = new FreeStandingTransactionOutput(networkParameters, u);
        Script multisigScript_ = multisigOutput_.getScriptPubKey();

        byte[] payloadBytes = Utils.HEX.decode((String) payMultiSign_.getBlockhashHex());
        Transaction transaction0 = networkParameters.getDefaultSerializer().makeTransaction(payloadBytes);

        Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript_, Transaction.SigHash.ALL, false);

        TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash, aesKey),
                Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature party1Signature = ecKey.sign(transaction0.getHash(), aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        requestParam.clear();
        requestParam.put("orderid", (String) payMultiSign_.getOrderid());
        requestParam.put("pubKey", ecKey.getPublicKeyAsHex());
        requestParam.put("signature", Utils.HEX.encode(buf1));
        requestParam.put("signInputData", Utils.HEX.encode(transactionSignature.encodeToBitcoin()));
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignResponse payMultiSignResponse = Json.jsonmapper().readValue(resp, PayMultiSignResponse.class);
        boolean success = payMultiSignResponse.isSuccess();
        if (success) {
            requestParam.clear();
            requestParam.put("orderid", (String) payMultiSign_.getOrderid());
            resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));

            PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                    PayMultiSignAddressListResponse.class);
            List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse
                    .getPayMultiSignAddresses();

            List<byte[]> sigs = new ArrayList<byte[]>();
            for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
                String signInputDataHex = payMultiSignAddress.getSignInputDataHex();
                sigs.add(Utils.HEX.decode(signInputDataHex));
            }

            Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs);
            transaction0.getInput(0).setScriptSig(inputScript);

            byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.addTransaction(transaction0);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        }
    }

    public KeyParameter getAesKey() {
        return aesKey;
    }

    public void setAesKey(KeyParameter aesKey) {
        this.aesKey = aesKey;
    }

    public boolean isSellFlag() {
        return sellFlag;
    }

    public void setSellFlag(boolean sellFlag) {
        this.sellFlag = sellFlag;
    }

}
