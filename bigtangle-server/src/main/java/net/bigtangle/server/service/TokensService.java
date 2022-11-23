/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullBlockStore;

@Service
public class TokensService {

    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    UserDataService userDataService;
    private static final Logger logger = LoggerFactory.getLogger(TokensService.class);

    public AbstractResponse getTokenById(String tokenid, FullBlockStore store) throws BlockStoreException {
        List<Token> tokens = store.getTokenID(tokenid);
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getToken(String blockhashString, FullBlockStore store) throws BlockStoreException {
        List<Token> tokens = new ArrayList<>();
        tokens.add(store.getTokenByBlockHash(Sha256Hash.wrap(blockhashString)));
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getMarketTokensList(FullBlockStore store) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getMarketTokenList());
        return GetTokensResponse.create(list);
    }

    public GetTokensResponse searchTokens(String name, FullBlockStore store) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getTokensList(name));
        Map<String, BigInteger> map = getTokenAmountMap(list);
        return GetTokensResponse.create(list, map);
    }

    public Map<String, BigInteger> getTokenAmountMap(List<Token> list) throws BlockStoreException {
        Map<String, BigInteger> map = new HashMap<String, BigInteger>();
        for (Token t : list) {
            BigInteger id = map.get(t.getTokenid());
            if (id == null) {
                map.put(t.getTokenid(), t.getAmount());
            } else {
                map.put(t.getTokenid(), id.add(t.getAmount()));
            }
        }
        return map;
    }

 
    

}
