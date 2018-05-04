/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Tokens;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetTokensResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.MonetaryFormat;

@Service
public class TokensService {

    public AbstractResponse getTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();

        Tokens tokens = new Tokens();
        tokens.setTokenid(NetworkParameters.BIGNETCOIN_TOKENID);
        tokens.setTokenname(MonetaryFormat.CODE_BTC);
        tokens.setAmount(100000L);
        // tokens.setDescription("default");
        tokens.setBlocktype((int) NetworkParameters.BLOCKTYPE_GENESIS);

        list.add(tokens);
        list.addAll(store.getTokensList());

        return GetTokensResponse.create(list);
    }

    public AbstractResponse getTokensList(String name) throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();

        Tokens tokens = new Tokens();
        tokens.setTokenid(NetworkParameters.BIGNETCOIN_TOKENID);
        tokens.setTokenname(MonetaryFormat.CODE_BTC);
        tokens.setAmount(100000L);
        // tokens.setDescription("default");
        tokens.setBlocktype((int) NetworkParameters.BLOCKTYPE_GENESIS);

        list.add(tokens);
        list.addAll(store.getTokensList(name));

        return GetTokensResponse.create(list);
    }

    @Autowired
    protected FullPrunedBlockStore store;
}
