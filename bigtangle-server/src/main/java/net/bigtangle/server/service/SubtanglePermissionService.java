package net.bigtangle.server.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.SubtangleResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.SubtangleStatus;

@Service
public class SubtanglePermissionService {

    @Autowired
    protected FullPrunedBlockStore store;

    public void savePubkey(String pubkey) throws BlockStoreException {
        store.deleteSubtanglePermission(pubkey);
        store.insertSubtanglePermission(pubkey, null, SubtangleStatus.wait);
    }

    public AbstractResponse getSubtanglePermissionList(List<String> pubkeys) throws BlockStoreException {

        List<Map<String, String>> maps = store.getSubtanglePermissionListByPubkeys(pubkeys);
        return SubtangleResponse.createUserDataResponse(maps);
    }

    public AbstractResponse getAllSubtanglePermissionList() throws BlockStoreException {

        List<Map<String, String>> maps = store.getAllSubtanglePermissionList();
        return SubtangleResponse.createUserDataResponse(maps);
    }

    public AbstractResponse getSubtanglePermissionList(String pubkey) throws BlockStoreException {

        List<Map<String, String>> maps = store.getSubtanglePermissionListByPubkey(pubkey);
        return SubtangleResponse.createUserDataResponse(maps);
    }
}
