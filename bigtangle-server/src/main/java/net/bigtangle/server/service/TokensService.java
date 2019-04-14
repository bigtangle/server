/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TokensService {

    public AbstractResponse getTokenById(String tokenid) throws BlockStoreException {
        List<Token> tokens = this.store.getTokenID(tokenid);
        AbstractResponse response = GetTokensResponse.create(tokens);
        return response;
    }

    public AbstractResponse getMarketTokensList() throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getMarketTokenList());
        return GetTokensResponse.create(list);
    }

    public AbstractResponse getTokensList() throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getTokensList(new HashSet<String>()));
        return GetTokensResponse.create(list);
    }

    public AbstractResponse getTokensList(String name) throws BlockStoreException {
        List<Token> list = new ArrayList<Token>();
        list.addAll(store.getTokensList(name));
        Map<String, Long> map = store.getTokenAmountMap(name);
        return GetTokensResponse.create(list, map);
    }

    public AbstractResponse getTokenSerialListById(String tokenid, List<String> addresses) throws BlockStoreException {
        List<TokenSerial> tokenSerials = this.store.getSearchTokenSerialInfo(tokenid, addresses);
        AbstractResponse response = GetTokensResponse.createTokenSerial(tokenSerials);
        return response;
    }

    public void updateTokenInfo(Block block) throws Exception {
        this.store.beginDatabaseBatchWrite();
        try {
            if (block.getTransactions() == null || block.getTransactions().isEmpty()) {
                return;
            }
            Transaction transaction = block.getTransactions().get(0);
            if (transaction.getData() == null) {
                return;
            }
            byte[] buf = transaction.getData();
            TokenInfo tokenInfo = TokenInfo.parse(buf);

            final String tokenid = tokenInfo.getToken().getTokenid();
            Token tokens = this.store.getToken(tokenInfo.getToken().getBlockhash());
            if (tokens != null) {
                throw new BlockStoreException("token can't update");
            }
            Token tokens2 = tokenInfo.getToken();
            List<MultiSign> multiSigns = this.store.getMultiSignListByTokenid(tokenid, tokens2.getTokenindex());
            int signnumber = 0;
            for (MultiSign multiSign : multiSigns) {
                if (multiSign.getSign() == 1) {
                    signnumber++;
                }
            }
            if (signnumber >= multiSigns.size()) {
                throw new BlockStoreException("token can't update");
            }
            this.store.deleteMultiSign(tokenid);
            multiSignService.multiSign(block, true);
        } catch (Exception e) {
            e.printStackTrace();
            this.store.abortDatabaseBatchWrite();
        }
        this.store.commitDatabaseBatchWrite();
    }

    @Autowired
    private MultiSignService multiSignService;

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected FullPrunedBlockStore store;
}
