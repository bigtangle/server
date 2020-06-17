package net.bigtangle.server.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.data.SubtangleStatus;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.SubtangleResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class SubtanglePermissionService {

  

    public boolean savePubkey(String pubkey, String signHex, FullPrunedBlockStore store) throws BlockStoreException {
        ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));

        byte[] signOutput = Utils.HEX.decode(signHex);
        boolean flag = key.verify(Sha256Hash.ZERO_HASH.getBytes(), signOutput);
        if (flag) {
            store.deleteSubtanglePermission(pubkey);
            store.insertSubtanglePermission(pubkey, null, SubtangleStatus.wait);
        }
        return flag;

    }

    public void updateSubtanglePermission(String pubkey, String signHex, String userdataPubkey, String status, FullPrunedBlockStore store)
            throws BlockStoreException {

            store.updateSubtanglePermission(pubkey, userdataPubkey, status);
 

    }

    public AbstractResponse getSubtanglePermissionList(List<String> pubkeys, FullPrunedBlockStore store) throws BlockStoreException {

        List<Map<String, String>> maps = store.getSubtanglePermissionListByPubkeys(pubkeys);
        return SubtangleResponse.createUserDataResponse(maps);
    }

    public AbstractResponse getAllSubtanglePermissionList(  FullPrunedBlockStore store) throws BlockStoreException {

        List<Map<String, String>> maps = store.getAllSubtanglePermissionList();
        return SubtangleResponse.createUserDataResponse(maps);
    }

    public AbstractResponse getSubtanglePermissionList(String pubkey, FullPrunedBlockStore store) throws BlockStoreException {

        List<Map<String, String>> maps = store.getSubtanglePermissionListByPubkey(pubkey);
        return SubtangleResponse.createUserDataResponse(maps);
    }
}
