/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetTokensResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.MonetaryFormat;

@Service
public class TokensService {
    public AbstractResponse getTokenById(String tokenid) throws BlockStoreException {
        Tokens tokens = this.store.getTokensInfo(tokenid);
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getMarketTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();

        list.addAll(store.getMarketTokenList());
        return GetTokensResponse.create(list);
    }
    public AbstractResponse getTokensList() throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        Tokens tokens = new Tokens();
        tokens.setTokenid(Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID));
        tokens.setTokenname(MonetaryFormat.CODE_BTC);
        list.add(tokens);
        list.addAll(store.getTokensList());
        return GetTokensResponse.create(list);
    }
    public AbstractResponse getTokensList(String name) throws BlockStoreException {
        List<Tokens> list = new ArrayList<Tokens>();
        Tokens tokens = new Tokens();
        tokens.setTokenid(Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID));
        tokens.setTokenname(MonetaryFormat.CODE_BTC);
        list.add(tokens);
        list.addAll(store.getTokensList(name));
        Map<String, Long> map = store.getTokenAmountMap(name);
        return GetTokensResponse.create(list, map);
    }

    public AbstractResponse getTokenSerialListById(String tokenid, List<String> addresses) throws BlockStoreException {
        List<TokenSerial> tokenSerials = this.store.getSearchTokenSerialInfo(tokenid,addresses);
        AbstractResponse response = GetTokensResponse.createTokenSerial(tokenSerials);
        return response;
    }
    
    public void updateTokenInfo(Block block) throws Exception {
        if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
            return;
        }
        Transaction transaction = block.getTransactions().get(0);
        if (transaction.getData() == null) {
            return;
        }
        byte[] buf = transaction.getData();
        TokenInfo tokenInfo = new TokenInfo().parse(buf);
        
        final String tokenid = tokenInfo.getTokens().getTokenid();
        Tokens tokens = this.store.getTokensInfo(tokenid);
        if (tokens != null) {
            throw new BlockStoreException("token can't update");
        }
        
        TokenSerial tokenSerial = tokenInfo.getTokenSerial();
        List<MultiSign> multiSigns = this.store.getMultiSignListByTokenid(tokenid, tokenSerial.getTokenindex());
        int signnumber = 0;
        for (MultiSign multiSign : multiSigns) {
            if (multiSign.getSign() == 1) {
                signnumber ++;
            }
        }
        if (signnumber >= multiSigns.size()) {
            throw new BlockStoreException("token can't update");
        }
        this.store.deleteMultiSign(tokenid);
        multiSignService.multiSign(block);
    }
    
    @Autowired
    private MultiSignService multiSignService;
    
    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected FullPrunedBlockStore store;
}
