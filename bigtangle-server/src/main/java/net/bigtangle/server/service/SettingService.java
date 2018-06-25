package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.SettingResponse;
import net.bigtangle.store.DatabaseFullPrunedBlockStore;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class SettingService {

    public AbstractResponse clientVersion() throws BlockStoreException {
        byte[] version = this.store.getSettingValue(DatabaseFullPrunedBlockStore.VERSION_SETTING);
        String value = new String(version);
        return SettingResponse.create(value);
    }
    
    @Autowired
    protected FullPrunedBlockStore store;
}
