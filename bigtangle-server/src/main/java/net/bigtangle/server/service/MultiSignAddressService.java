package net.bigtangle.server.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.Tokens;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetMultiSignAddressResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class MultiSignAddressService {

    public AbstractResponse getMultiSignAddressList(String tokenid) throws BlockStoreException {
        List<MultiSignAddress> list = store.getMultiSignAddressListByTokenid(tokenid);
        GetMultiSignAddressResponse response = GetMultiSignAddressResponse.create(list);
        return response;
    }

    @Autowired
    protected FullPrunedBlockStore store;

    public void addMultiSignAddress(Map<String, Object> request) throws BlockStoreException {
        String tokenid = (String) request.get("tokenid");
        Tokens tokens = this.store.getTokensInfo(tokenid);
        if (tokens == null) {
            throw new BlockStoreException("tokens not exist");
        }
        int count = this.store.getCountMultiSignAddress(tokens.getTokenid());
        if (tokens.getSignnumber() <= count) {
            throw new BlockStoreException("tokens signnumber error");
        }
        String address = (String) request.get("address");
        MultiSignAddress multiSignAddress = new MultiSignAddress(tokens.getTokenid(), address);
        store.insertMultiSignAddress(multiSignAddress);
    }

    public void delMultiSignAddress(Map<String, Object> request) throws BlockStoreException {
        String tokenid = (String) request.get("tokenid");
        String address = (String) request.get("address");
        store.deleteMultiSignAddress(tokenid, address);
    }

}
