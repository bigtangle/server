package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.UserDataResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.ApiCall;
import net.bigtangle.store.FullBlockStore;

@Service
public class UserDataService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);
    @Autowired
    ServerConfiguration serverConfiguration;

    public byte[] getUserData(String dataclassname, String pubKey, FullBlockStore store) throws BlockStoreException {
        UserData userData = store.queryUserDataWithPubKeyAndDataclassname(dataclassname, pubKey);
        if (userData != null) {
            return userData.getData();
        }
        return new byte[0];
    }

    public AbstractResponse getUserDataList(int blocktype, List<String> pubKeyList, FullBlockStore store)
            throws BlockStoreException {
        List<UserData> userDatas = store.getUserDataListWithBlocktypePubKeyList(blocktype, pubKeyList);
        List<String> dataList = new ArrayList<String>();
        for (UserData userData : userDatas) {
            if (userData.getData() == null) {
                continue;
            }
            dataList.add(Utils.HEX.encode(userData.getData()));
        }
        return UserDataResponse.createUserDataResponse(dataList);
    }

    public boolean ipCheck(String reqCmd, byte[] contentBytes, HttpServletResponse httpServletResponse,
            HttpServletRequest httprequest) {
        String remoteAddr = remoteAddr(httprequest);
        if ("81.169.156.203".equals(remoteAddr) || "61.181.128.236".equals(remoteAddr)
                || "61.181.128.230".equals(remoteAddr)) {
            return true;
        }

        if (serverConfiguration.getDeniedIPlist().contains(remoteAddr)) {
            return false;
        } 
       
        return true;
    }

    Long lastUpdate = 0l;
    List<ApiCall> staticsticCalls = new ArrayList<ApiCall>();
    List<String> denieds = new ArrayList<String>();
    
    public  void calcDenied() {
        if (staticsticCalls == null || lastUpdate < System.currentTimeMillis() - 20000) {
            lastUpdate = System.currentTimeMillis();
        }
      
    }

    public String remoteAddr(HttpServletRequest request) {
        String remoteAddr = "";
        remoteAddr = request.getHeader("X-FORWARDED-FOR");
        if (remoteAddr == null || "".equals(remoteAddr)) {
            remoteAddr = request.getRemoteAddr();
        } else {
            StringTokenizer tokenizer = new StringTokenizer(remoteAddr, ",");
            while (tokenizer.hasMoreTokens()) {
                remoteAddr = tokenizer.nextToken();
                break;
            }
        }
        return remoteAddr;
    }

    public String[] serverSeeds() {
        return new String[] { "81.169.156.203", "61.181.128.236", "61.181.128.230" };

    }
}
