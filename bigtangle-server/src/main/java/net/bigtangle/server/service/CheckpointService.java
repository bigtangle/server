/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Coin;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.data.Tokensums;
import net.bigtangle.core.data.TokensumsMap;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * <p>
 * For a given confirmed reward block as checkpoint, this value from the
 * database must be the same for all servers. This checkpoint is saved as
 * checkpoint block and can be verified and enable fast setup and pruned history
 * data. The checkpoint can be used as the new genesis state.
 * </p>
 */
@Service
public class CheckpointService {
    @Autowired
    protected  StoreService storeService;

   // private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

    private List<UTXO> getOutputs(String tokenid,FullPrunedBlockStore store) throws UTXOProviderException, BlockStoreException {
        //Must be sorted with the  key of 
        return store.getOpenAllOutputs(tokenid);
    }

    public Coin ordersum(String tokenid, List<OrderRecord> orders) throws JsonProcessingException, Exception {
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        for (OrderRecord orderRecord : orders) {
            if (orderRecord.getOfferTokenid().equals(tokenid)) {
                sumUnspent = sumUnspent.add(Coin.valueOf(orderRecord.getOfferValue(), tokenid));
            }
        }
        return sumUnspent;
    }

    private List<OrderRecord> orders(String tokenid,FullPrunedBlockStore store) throws BlockStoreException {
        return store.getAllOpenOrdersSorted(null, tokenid);

    }

    public Map<String, BigInteger> tokensumInitial(FullPrunedBlockStore store) throws BlockStoreException {

        return store.getTokenAmountMap();
    }

    public TokensumsMap checkToken(FullPrunedBlockStore store) throws BlockStoreException, UTXOProviderException {

        TokensumsMap tokensumset = new TokensumsMap();

        Map<String, BigInteger> tokensumsInitial = tokensumInitial(store);
        Set<String> tokenids = tokensumsInitial.keySet();
        for (String tokenid : tokenids) {
            Tokensums tokensums = new Tokensums();
            tokensums.setTokenid(tokenid);
            tokensums.setUtxos(getOutputs(tokenid,store));
            tokensums.setOrders(orders(tokenid,store));
            tokensums.setInitial(tokensumsInitial.get(tokenid));
            tokensums.calculate();

        }
        return tokensumset;
    }
}
