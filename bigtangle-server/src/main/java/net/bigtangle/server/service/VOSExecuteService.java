package net.bigtangle.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.VOSExecuteListResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class VOSExecuteService {

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getVOSExecuteList(String vosKey) throws BlockStoreException {
        List<VOSExecute> vosExecutes = this.store.getVOSExecuteList(vosKey);
        return VOSExecuteListResponse.create(vosExecutes);
    }
}
