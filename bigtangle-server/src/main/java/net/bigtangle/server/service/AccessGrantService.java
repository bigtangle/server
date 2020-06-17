package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class AccessGrantService {

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    protected  StoreService storeService;
    
    public void addAccessGrant(String pubKey,FullPrunedBlockStore store) throws BlockStoreException {
        byte[] buf = Utils.HEX.decode(pubKey);
        ECKey ecKey = ECKey.fromPublicOnly(buf);
        Address address = ecKey.toAddress(networkParameters); 
        store. insertAccessGrant(address.toBase58());
    
    }

    public void deleteAccessGrant(String pubKey,FullPrunedBlockStore store) throws BlockStoreException 
    {
        byte[] buf = Utils.HEX.decode(pubKey);
        ECKey ecKey = ECKey.fromPublicOnly(buf);
        Address address = ecKey.toAddress(networkParameters);
        store .deleteAccessGrant(address.toBase58());
    }

    public int getCountAccessGrantByAddress(String address, FullPrunedBlockStore store) {
        try {
            int count =  store.getCountAccessGrantByAddress(address);
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
