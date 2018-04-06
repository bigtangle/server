package com.bignetcoin.server.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.response.AbstractResponse;
import com.bignetcoin.server.response.GetTokensResponse;
import com.bignetcoin.store.FullPrunedBlockStore;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Tokens;

@Service
public class TokensService {

    public AbstractResponse getTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        
        Tokens tokens = new Tokens();
        tokens.setTokenid(NetworkParameters.BIGNETCOIN_TOKENID);
        tokens.setTokenname("default");
        tokens.setAmount(100000L);
        tokens.setDescription("default");
        tokens.setBlocktype((int) NetworkParameters.BLOCKTYPE_GENESIS);
        
        list.add(tokens);
        list.addAll(store.getTokensList());
        
        return GetTokensResponse.create(list);
    }

    @Autowired
    protected FullPrunedBlockStore store;
}
