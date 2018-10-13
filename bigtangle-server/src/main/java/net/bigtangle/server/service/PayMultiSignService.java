package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.PayMultiSignExt;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignAddressListResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignListResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class PayMultiSignService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    private NetworkParameters networkParameters;

    public AbstractResponse getPayMultiSignDetails(String orderid) throws BlockStoreException {
        PayMultiSign payMultiSign = this.store.getPayMultiSignWithOrderid(orderid);
        return PayMultiSignDetailsResponse.create(payMultiSign);
    }
 

    public void launchPayMultiSign(byte[] data) throws BlockStoreException, Exception {
        PayMultiSign payMultiSign = convertTransactionDataToPayMultiSign(data);

        String hashhex = payMultiSign.getOutputHashHex();
        long index = payMultiSign.getOutputsindex();
        List<OutputsMulti> outputsMultis = this.store.queryOutputsMultiByHashAndIndex(Utils.HEX.decode(hashhex), index);

        if (outputsMultis.isEmpty()) {
            throw new BlockStoreException("multisignaddress list is empty");
        }
        // check param
        this.store.insertPayPayMultiSign(payMultiSign);
        int i = 0;
        for (OutputsMulti outputsMulti : outputsMultis) {
            PayMultiSignAddress payMultiSignAddress = new PayMultiSignAddress();
            payMultiSignAddress.setOrderid(payMultiSign.getOrderid());
            payMultiSignAddress.setSign(0);
            payMultiSignAddress.setPubKey(outputsMulti.getToAddress());
            payMultiSignAddress.setSignIndex(i);
            this.store.insertPayMultiSignAddress(payMultiSignAddress);
            i++;
        }
    }

 
    public AbstractResponse payMultiSign(Map<String, Object> request) throws BlockStoreException, Exception {
        String orderid = (String) request.get("orderid");

        PayMultiSign payMultiSign_ = this.store.getPayMultiSignWithOrderid(orderid);

        List<PayMultiSignAddress> payMultiSignAddresses_ = this.store.getPayMultiSignAddressWithOrderid(orderid);
        HashMap<String, PayMultiSignAddress> payMultiSignAddresseRes = new HashMap<String, PayMultiSignAddress>();
        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses_)
            payMultiSignAddresseRes.put(payMultiSignAddress.getPubKey(), payMultiSignAddress);
        if (payMultiSignAddresseRes.isEmpty()) {
            throw new BlockStoreException("pay multisign addresse res is empty");
        }

        String pubKey0 = (String) request.get("pubKey");
        String address0 = ECKey.fromPublicOnly(Utils.HEX.decode(pubKey0)).toAddress(networkParameters).toString();
        if (!payMultiSignAddresseRes.containsKey(address0)) {
            throw new BlockStoreException("pay multisign addresse list is empty");
        }

        Transaction transaction = networkParameters.getDefaultSerializer()
                .makeTransaction(payMultiSign_.getBlockhash());

        byte[] pubKey = Utils.HEX.decode(pubKey0);
        byte[] data = transaction.getHash().getBytes();
        byte[] signature = Utils.HEX.decode((String) request.get("signature"));
        boolean success = ECKey.verify(data, signature, pubKey);
        if (!success) {
            throw new BlockStoreException("multisign signature error");
        }

        // int signIndex =
        // this.store.getMaxPayMultiSignAddressSignIndex(orderid);
        // signIndex++;

        byte[] signInputData = Utils.HEX.decode((String) request.get("signInputData"));
        this.store.updatePayMultiSignAddressSign(orderid, address0, 1, signInputData);

        int count = this.store.getCountPayMultiSignAddressStatus(orderid);
        if (payMultiSign_.getMinsignnumber() <= count) {
            return PayMultiSignResponse.create(true);
        } else {
            return PayMultiSignResponse.create(false);
        }
    }

    private PayMultiSign convertTransactionDataToPayMultiSign(byte[] data) throws BlockStoreException, Exception {
        String jsonStr = new String(data);
        PayMultiSign payMultiSign = Json.jsonmapper().readValue(jsonStr, PayMultiSign.class);
        payMultiSign.setBlockhash(Utils.HEX.decode(payMultiSign.getBlockhashHex()));
        return payMultiSign;
    }

    public AbstractResponse getPayMultiSignList(List<String> pubKeys) throws BlockStoreException {
        List<PayMultiSign> payMultiSigns = this.store.getPayMultiSignList(pubKeys);
        List<PayMultiSignExt> payMultiSignExts = new ArrayList<PayMultiSignExt>();
        for (PayMultiSign payMultiSign : payMultiSigns) {
            PayMultiSignExt payMultiSignExt = new PayMultiSignExt();
            payMultiSignExt.setAmount(payMultiSign.getAmount());
            payMultiSignExt.setBlockhash(payMultiSign.getBlockhash());
            payMultiSignExt.setBlockhashHex(payMultiSign.getBlockhashHex());
            payMultiSignExt.setMinsignnumber(payMultiSign.getMinsignnumber());
            payMultiSignExt.setOrderid(payMultiSign.getOrderid());
            payMultiSignExt.setToaddress(payMultiSign.getToaddress());
            payMultiSignExt.setTokenid(payMultiSign.getTokenid());
            payMultiSignExt.setOutputHashHex(payMultiSign.getOutputHashHex());
            payMultiSignExt.setSign(payMultiSign.getSign());
            payMultiSignExt.setRealSignnumber(payMultiSign.getSigncount());
            payMultiSignExts.add(payMultiSignExt);
        }
        return PayMultiSignListResponse.create(payMultiSignExts);
    }

    public AbstractResponse getPayMultiSignAddressList(String orderid) throws BlockStoreException {
        List<PayMultiSignAddress> payMultiSignAddresses = this.store.getPayMultiSignAddressWithOrderid(orderid);
        return PayMultiSignAddressListResponse.create(payMultiSignAddresses);
    }
}
