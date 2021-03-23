package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

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
            logger.debug(serverConfiguration.getDeniedIPlist().toString());
            return false;
        }
        if (denieds.contains(remoteAddr)) {
            logger.debug(denieds.toString());
            return false;
        }

        return true;
    }

    public void addStatistcs(String reqCmd, String remoteAddr) {
        List<ApiCall> l = staticsticCalls.get(remoteAddr);
        if (l == null) {
            l = new ArrayList<ApiCall>();
            l.add(new ApiCall(remoteAddr, reqCmd, System.currentTimeMillis()));
            ;
            staticsticCalls.put(remoteAddr, l);
        } else {
            l.add(new ApiCall(remoteAddr, reqCmd, System.currentTimeMillis()));
        }
    }

    Map<String, List<ApiCall>> staticsticCalls = new HashMap<String, List<ApiCall>>();
    Set<String> denieds = new HashSet<String>();

    // last 15 seconds schedule interval
    // call getbalance 5 times , as attack
    public synchronized void calcDenied() {
        for (Entry<String, List<ApiCall>> a : staticsticCalls.entrySet()) {
            ApiCall max = a.getValue().stream().min(Comparator.comparing(ApiCall::getTime)).get();
            List<ApiCall> s = a.getValue().stream().filter(c -> max !=null && c !=null && c.getTime() > (max.getTime() - 15000))
                    .collect(Collectors.toList());
            logger.debug("a.getKey() 15s calls =  " + a.getKey() + " -> " + s.size());
            if (s.size() > 8) {
                logger.debug("add to denied = "+ a.getKey());
                denieds.add(a.getKey());
            }
        }
        staticsticCalls = new HashMap<String, List<ApiCall>>();
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
