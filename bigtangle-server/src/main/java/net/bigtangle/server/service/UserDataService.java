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
                || "61.181.128.230".equals(remoteAddr)
                || "127.0.0.1".equals(remoteAddr)) {
            return true;
        }else {
            return false;
        }
        /*
        if (serverConfiguration.getDeniedIPlist().contains(remoteAddr)) {
            logger.debug(serverConfiguration.getDeniedIPlist().toString());
            return false;
        }
        if (denieds.contains(remoteAddr)) {
            logger.debug(denieds.toString());
            return false;
        }

        return true;
        */
    }

    public void addStatistcs(String reqCmd, String remoteAddr) {
        List<ApiCall> l = staticsticCalls.get(remoteAddr);
        if (l == null) {
            l = new ArrayList<ApiCall>();
            l.add(new ApiCall(remoteAddr, reqCmd, System.currentTimeMillis()));
            staticsticCalls.put(remoteAddr, l);
        } else {
            l.add(new ApiCall(remoteAddr, reqCmd, System.currentTimeMillis()));
        }
        calcDenied();
    }

    Map<String, List<ApiCall>> staticsticCalls = new HashMap<String, List<ApiCall>>();
    Set<String> denieds = new HashSet<String>();
    Long updatetime = 0l;

    // last 15 seconds schedule interval
    // call api 15 times per seconds, as attack
    public synchronized void calcDenied() {
        try {
            for (Entry<String, List<ApiCall>> a : staticsticCalls.entrySet()) {
                ApiCall max = a.getValue().stream().min(Comparator.comparing(ApiCall::getTime)).get();
                List<ApiCall> s = a.getValue().stream()
                        .filter(c -> max != null && c != null && c.getTime() > (max.getTime() - 5000))
                        .collect(Collectors.toList());
                logger.debug("a.getKey() 15s calls =  " + a.getKey() + " -> " + s.size());
                if (s.size() > 4) {
                    logger.debug("add to denied = " + a.getKey());
                    for (ApiCall l : s) {
                        logger.debug("  " + l.toString());
                    }
                    denieds.add(a.getKey());
                }
            }
        } catch (Exception e) {
            logger.debug("", e);
        }
        staticsticCalls = new HashMap<String, List<ApiCall>>();
        if (updatetime < System.currentTimeMillis() - 1 * 60 * 60 * 1000) {
            logger.debug("reset denied  ");
            denieds = new HashSet<String>();
            updatetime = System.currentTimeMillis();
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
