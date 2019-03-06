package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.UserDataResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class UserDataService {

    @Autowired
    protected FullPrunedBlockStore store;

    public byte[] getUserData(String dataclassname, String pubKey) throws BlockStoreException {
        UserData userData = this.store.queryUserDataWithPubKeyAndDataclassname(dataclassname, pubKey);
        if (userData != null) {
            return userData.getData();
        }
        return new byte[0];
    }

    public AbstractResponse getUserDataList(int blocktype, List<String> pubKeyList) throws BlockStoreException {
        List<UserData> userDatas = this.store.getUserDataListWithBlocktypePubKeyList(blocktype, pubKeyList);
        List<String> dataList = new ArrayList<String>();
        for (UserData userData : userDatas) {
            if (userData.getData() == null) {
                continue;
            }
            dataList.add(Utils.HEX.encode(userData.getData()));
        }
        return UserDataResponse.createUserDataResponse(dataList);
    }
}
