package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.SettingResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class SettingService {

    @Autowired
    ServerConfiguration serverConfiguration;

    public AbstractResponse clientVersion() throws BlockStoreException {
        String value = serverConfiguration.getClientversion();
        return SettingResponse.create(value);
    }

    public boolean getPermissionFlag() throws BlockStoreException {
        Boolean value = serverConfiguration.getPermissioned();
        return value;
    }

    @Autowired
    protected FullPrunedBlockStore store;
}
