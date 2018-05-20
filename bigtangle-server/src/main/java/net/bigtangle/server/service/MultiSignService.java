package net.bigtangle.server.service;

import java.util.List;

import javax.swing.AbstractAction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.MultiSign;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.MultiSignResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class MultiSignService {

    @Autowired
    protected FullPrunedBlockStore store;
    
    public AbstractResponse getMultiSignList(String address) throws BlockStoreException {
        List<MultiSign> multiSigns = this.store.getMultiSignListByAddress(address);
        return MultiSignResponse.createMultiSignResponse(multiSigns);
    }
}
