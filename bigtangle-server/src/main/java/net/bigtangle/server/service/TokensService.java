/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TokensService {

    public AbstractResponse getTokenById(String tokenid) throws BlockStoreException {
        List<Token> tokens = this.store.getTokenID(tokenid);
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getToken(String blockhashString) throws BlockStoreException {
        List<Token> tokens = new ArrayList<>();
        tokens.add(this.store.getTokenByBlockHash(Sha256Hash.wrap(blockhashString)));
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getMarketTokensList() throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getMarketTokenList());
        return GetTokensResponse.create(list);
    }

 
    public GetTokensResponse searchTokens(String name) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getTokensList(name));
        Map<String, BigInteger> map = store.getTokenAmountMap();
        return GetTokensResponse.create(list, map);
    }

 
   
 
    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected FullPrunedBlockStore store;
}
