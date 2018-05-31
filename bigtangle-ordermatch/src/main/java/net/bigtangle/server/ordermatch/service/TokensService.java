/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ordermatch.service.response.AbstractResponse;
import net.bigtangle.server.ordermatch.service.response.GetTokensResponse;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;
import net.bigtangle.utils.MonetaryFormat;

@Service
public class TokensService {

    public AbstractResponse getTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        Tokens tokens = new Tokens();
        tokens.setTokenid(Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID));
        tokens.setTokenname(MonetaryFormat.CODE_BTC);
        list.add(tokens);
        list.addAll(store.getTokensList());
        return GetTokensResponse.create(list);
    }
    
    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected FullPrunedBlockStore store;
}
