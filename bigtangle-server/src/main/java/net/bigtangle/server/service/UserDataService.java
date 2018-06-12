package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.UserData;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class UserDataService {

    @Autowired
    protected FullPrunedBlockStore store;
    
    public byte[] getUserData(String dataclassname, String pubKey) throws BlockStoreException {
        UserData userData = this.store.getUserDataByPrimaryKey(dataclassname, pubKey);
        if (userData != null) {
            return userData.getData();
        }
        return new byte[0];
    }
}
