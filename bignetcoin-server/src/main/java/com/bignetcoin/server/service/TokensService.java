package com.bignetcoin.server.service;

import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Tokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.response.AbstractResponse;
import com.bignetcoin.server.response.GetTokensResponse;
import com.bignetcoin.store.FullPrunedBlockStore;

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
