package net.bigtangle.server.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class PayMultiSignService {
    
    @Autowired
    protected FullPrunedBlockStore store;

    public void launchPayMultiSign(Block block) throws BlockStoreException, Exception {
        PayMultiSign payMultiSign = convertTransactionDataToPayMultiSign(block);
        
        String tokenid = payMultiSign.getTokenid();
        Tokens tokens = this.store.getTokensInfo(tokenid);
        if (tokens == null) {
            throw new BlockStoreException("token not existed");
        }
        
        if (tokens.getSignnumber() < 2) {
            throw new BlockStoreException("token can't multi sign");
        }
        
        List<MultiSignAddress> multiSignAddresses = this.store.getMultiSignAddressListByTokenid(tokens.getTokenid());
        if (multiSignAddresses.isEmpty()) 
            throw new BlockStoreException("multisignaddress list is empty");
        
        // check param
        this.store.insertPayPayMultiSign(payMultiSign);
        for (MultiSignAddress multiSignAddress : multiSignAddresses) {
            PayMultiSignAddress payMultiSignAddress = new PayMultiSignAddress();
            payMultiSignAddress.setOrderid(payMultiSign.getOrderid());
            payMultiSignAddress.setSign(0);
            payMultiSignAddress.setPubKey(multiSignAddress.getPubKeyHex());
            this.store.insertPayMultiSignAddress(payMultiSignAddress);
        }
    }
    
    public void payMultiSign(Block block) throws BlockStoreException, Exception{
        PayMultiSign payMultiSign_ = convertTransactionDataToPayMultiSign(block);
        String orderid = payMultiSign_.getOrderid();
        
        PayMultiSign payMultiSign = this.store.getPayMultiSignWithOrderid(orderid);
        if (payMultiSign == null) 
            throw new BlockStoreException("pay multisign not existed");
        
        List<PayMultiSignAddress> payMultiSignAddresses_ = this.store.getPayMultiSignAddressWithOrderid(orderid);
        HashMap<String, PayMultiSignAddress> payMultiSignAddresseRes = new HashMap<String, PayMultiSignAddress>();
        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses_) payMultiSignAddresseRes.put(payMultiSignAddress.getPubKey(), payMultiSignAddress);
        if (payMultiSignAddresseRes.isEmpty()) 
            throw new BlockStoreException("pay multisign addresse res is empty");
        
        List<Map<String, Object>> payMultiSignAddresses = this.convertTransactionDataToPayMultiSignAddressList(block);
        Sha256Hash hash = block.getTransactions().get(0).getHash();
        int signCount = 0;
        for (Map<String, Object> payMultiSignAddress : payMultiSignAddresses) {
            String pubKeyStr = (String) payMultiSignAddress.get("pubKey");
            if (!payMultiSignAddresseRes.containsKey(pubKeyStr)) {
                throw new BlockStoreException("pay multisign addresse list is empty");
            }
            byte[] pubKey = Utils.HEX.decode(pubKeyStr);
            byte[] data = hash.getBytes();
            byte[] signature = Utils.HEX.decode((String) payMultiSignAddress.get("signature"));
            boolean success = ECKey.verify(data, signature, pubKey);
            if (success) {
                signCount++;
            } else {
                throw new BlockStoreException("multisign signature error");
            }
        }
        for (Map<String, Object> payMultiSignAddress : payMultiSignAddresses) {
            String pubKeyStr = (String) payMultiSignAddress.get("pubKey");
            this.store.updatePayMultiSignAddressSign(orderid, pubKeyStr, 1);
        }
        this.store.updatePayMultiSignBlockhash(orderid, block.bitcoinSerialize());
        if (signCount >= payMultiSign.getMinsignnumber()) {
            
        }
        // todo
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertTransactionDataToPayMultiSignAddressList(Block block) throws BlockStoreException, Exception {
        if (block.getTransactions().isEmpty()) {
            throw new BlockStoreException("block transaction is empty");
        }
        Transaction transaction = block.getTransactions().get(0);
        String _dataclassname = transaction.getDataclassname();
        if (StringUtils.isBlank(_dataclassname)) {
            throw new BlockStoreException("block transaction dataclassname is empty");
        }
        DataClassName dataClassName = DataClassName.valueOf(_dataclassname);
        if (dataClassName == null || dataClassName != DataClassName.PAYMULTISIGN) {
            throw new BlockStoreException("block transaction dataclassname error");
        }
        byte[] data = transaction.getDatasignatire();
        if (data == null || data.length == 0) {
            throw new BlockStoreException("transaction data error");
        }
        List<Map<String, Object>> payMultiSignAddresses = Json.jsonmapper().readValue(data, List.class);
        return payMultiSignAddresses;
    }

    private PayMultiSign convertTransactionDataToPayMultiSign(Block block) throws BlockStoreException, Exception {
        if (block.getTransactions().isEmpty()) {
            throw new BlockStoreException("block transaction is empty");
        }
        Transaction transaction = block.getTransactions().get(0);
        String _dataclassname = transaction.getDataclassname();
        if (StringUtils.isBlank(_dataclassname)) {
            throw new BlockStoreException("block transaction dataclassname is empty");
        }
        DataClassName dataClassName = DataClassName.valueOf(_dataclassname);
        if (dataClassName == null || dataClassName != DataClassName.PAYMULTISIGN) {
            throw new BlockStoreException("block transaction dataclassname error");
        }
        byte[] data = transaction.getData();
        if (data == null || data.length == 0) {
            throw new BlockStoreException("transaction data error");
        }
        PayMultiSign payMultiSign = Json.jsonmapper().readValue(data, PayMultiSign.class);
        return payMultiSign;
    }
}
