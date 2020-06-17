package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.PayMultiSignExt;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.PayMultiSignAddressListResponse;
import net.bigtangle.core.response.PayMultiSignDetailsResponse;
import net.bigtangle.core.response.PayMultiSignListResponse;
import net.bigtangle.core.response.PayMultiSignResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class PayMultiSignService {
 

    @Autowired
    private NetworkParameters networkParameters;

    public AbstractResponse getPayMultiSignDetails(String orderid, FullPrunedBlockStore store) throws BlockStoreException {
        PayMultiSign payMultiSign =  store.getPayMultiSignWithOrderid(orderid);
        return PayMultiSignDetailsResponse.create(payMultiSign);
    }
 

    public void launchPayMultiSign(byte[] data, FullPrunedBlockStore store) throws BlockStoreException, Exception {
        PayMultiSign payMultiSign = convertTransactionDataToPayMultiSign(data);

        String hashhex = payMultiSign.getOutputHashHex();
        long index = payMultiSign.getOutputindex();
        List<OutputsMulti> outputsMultis =  store.queryOutputsMultiByHashAndIndex(Utils.HEX.decode(hashhex), index);

        if (outputsMultis.isEmpty()) {
            throw new BlockStoreException("multisignaddress list is empty");
        }
        // check param
         store.insertPayPayMultiSign(payMultiSign);
        int i = 0;
        for (OutputsMulti outputsMulti : outputsMultis) {
            PayMultiSignAddress payMultiSignAddress = new PayMultiSignAddress();
            payMultiSignAddress.setOrderid(payMultiSign.getOrderid());
            payMultiSignAddress.setSign(0);
            payMultiSignAddress.setPubKey(outputsMulti.getToAddress());
            payMultiSignAddress.setSignIndex(i);
             store.insertPayMultiSignAddress(payMultiSignAddress);
            i++;
        }
    }

 
    public AbstractResponse payMultiSign(Map<String, Object> request, FullPrunedBlockStore store) throws BlockStoreException, Exception {
        String orderid = (String) request.get("orderid");

        PayMultiSign payMultiSign_ =  store.getPayMultiSignWithOrderid(orderid);

        List<PayMultiSignAddress> payMultiSignAddresses_ =  store.getPayMultiSignAddressWithOrderid(orderid);
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
         store.updatePayMultiSignAddressSign(orderid, address0, 1, signInputData);

        int count = store.getCountPayMultiSignAddressStatus(orderid);
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

    public AbstractResponse getPayMultiSignList(List<String> pubKeys, FullPrunedBlockStore store) throws BlockStoreException {
        List<PayMultiSign> payMultiSigns = store.getPayMultiSignList(pubKeys);
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
            payMultiSignExt.setOutputindex(  payMultiSign.getOutputindex());
            payMultiSignExt.setSign(payMultiSign.getSign());
            payMultiSignExt.setRealSignnumber(payMultiSign.getSigncount());
            payMultiSignExts.add(payMultiSignExt);
        }
        return PayMultiSignListResponse.create(payMultiSignExts);
    }

    public AbstractResponse getPayMultiSignAddressList(String orderid, FullPrunedBlockStore store) throws BlockStoreException {
        List<PayMultiSignAddress> payMultiSignAddresses =  store.getPayMultiSignAddressWithOrderid(orderid);
        return PayMultiSignAddressListResponse.create(payMultiSignAddresses);
    }
}
