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
    protected FullPrunedBlockStore store;
    @Autowired
    private NetworkParameters networkParameters;

    public void addAccessGrant(String pubKey) throws BlockStoreException {
        byte[] buf = Utils.HEX.decode(pubKey);
        ECKey ecKey = ECKey.fromPublicOnly(buf);
        Address address = ecKey.toAddress(networkParameters);
        this.store.insertAccessGrant(address.toBase58());
    }

    public void deleteAccessGrant(String pubKey) throws BlockStoreException {
        byte[] buf = Utils.HEX.decode(pubKey);
        ECKey ecKey = ECKey.fromPublicOnly(buf);
        Address address = ecKey.toAddress(networkParameters);
        this.store.deleteAccessGrant(address.toBase58());
    }

    public int getCountAccessGrantByAddress(String address) {
        try {
            int count = this.store.getCountAccessGrantByAddress(address);
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
