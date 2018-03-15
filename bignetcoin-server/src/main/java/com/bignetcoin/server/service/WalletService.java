/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package com.bignetcoin.server.service;

 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.response.AbstractResponse;
import com.bignetcoin.server.response.GetBalancesResponse;
import com.bignetcoin.server.transaction.FreeStandingTransactionOutput;
import com.google.common.collect.Lists;
 
@Service
public class WalletService {
    
    public AbstractResponse getAccountBalanceInfo(List<byte[]> pubKeyHashs) {
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs, true);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }

        HashMap<Long, Coin> tokens = new HashMap<Long, Coin>();
        long[] tokenIds = { NetworkParameters.BIGNETCOIN_TOKENID };
        for (long tokenid : tokenIds) {
            List<TransactionOutput> tmpTransactionOutputs = new ArrayList<>(transactionOutputs);
            filter(tmpTransactionOutputs, tokenid);
            CoinSelection selection = coinSelector.select(NetworkParameters.MAX_MONEY, tmpTransactionOutputs);
            Coin value = selection.valueGathered;
            tokens.put(tokenid, value);
        }
        
        return GetBalancesResponse.create(tokens, outputs);
    }
    
    private void filter(List<TransactionOutput> candidates, long tokenid) {
        for (Iterator<TransactionOutput> iterator = candidates.iterator(); iterator.hasNext();) {
            TransactionOutput transactionOutput = iterator.next();
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            if (freeStandingTransactionOutput.getUTXO().getTokenid() != tokenid) {
                iterator.remove();
            }
        }
    }

    public Wallet makeWallat(ECKey ecKey) {
        KeyChainGroup group = new KeyChainGroup(networkParameters);
        group.importKeys(ecKey);
        return new Wallet(networkParameters, group);
    }
    
    public void transactionCommit(ECKey fromKey, ECKey toKey, Coin amount) throws Exception {
        Wallet wallet = this.makeWallat(fromKey);
        Address address2 = new Address(networkParameters, toKey.getPubKeyHash());
        SendRequest req = SendRequest.to(address2, amount);
        wallet.completeTx(req);
        wallet.commitTx(req.tx);
    }
    
    @Autowired
    private NetworkParameters networkParameters;
    
    @Autowired
    protected FullPrunedBlockStore store;
    
    protected CoinSelector coinSelector = new DefaultCoinSelector();

    public LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(List<byte[]> pubKeyHashs,
            boolean excludeImmatureCoinbases) {
        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
        try {
            int chainHeight = store.getChainHeadHeight();
            for (UTXO output : getStoredOutputsFromUTXOProvider(pubKeyHashs)) {
                boolean coinbase = output.isCoinbase();
                int depth = chainHeight - output.getHeight() + 1; // the current
                                                                  // depth of
                                                                  // the output
                                                                  // (1 = same
                                                                  // as head).
                // Do not try and spend coinbases that were mined too recently,
                // the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= networkParameters.getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(networkParameters, output, chainHeight));
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        return candidates;
    }

    private List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs) throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        return store.getOpenTransactionOutputs(addresses);
    }

    public Coin getRealBalance(List<byte[]> pubKeyHashs) {
        return Coin.ZERO;
    }
}
