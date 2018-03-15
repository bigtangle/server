/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package com.bignetcoin.server.service;

 
import static org.bitcoinj.core.Utils.HEX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProvider;
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
public class WalletService0 {
    
    public List<UTXO> getAllSpendTransactionOutputList(List<byte[]> pubKeyHashs) {
        List<UTXO> outputs = new ArrayList<UTXO>();
        for (TransactionOutput transactionOutput : this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs, true)) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }
        return outputs;
    }
    
    public HashMap<Long, Coin> getRealBalanceFromCoinResult(List<byte[]> pubKeyHashs) {
        HashMap<Long, Coin> result = new HashMap<Long, Coin>();
        long[] tokenIds = { NetworkParameters.BIGNETCOIN_TOKENID };
        for (long tokenId : tokenIds) {
            Coin value = this.getRealBalance(pubKeyHashs, tokenId);
            result.put(tokenId, value);
        }
        return result;
    }
    
    private Coin getRealBalance(List<byte[]> pubKeyHashs, long tokenId) {
        List<TransactionOutput> candidates = calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs, true);
        for (Iterator<TransactionOutput> iterator = candidates.iterator(); iterator.hasNext();) {
            TransactionOutput transactionOutput = iterator.next();
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            
        }
        CoinSelection selection = coinSelector.select(NetworkParameters.MAX_MONEY, candidates);
        return selection.valueGathered;
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
    
    public AbstractResponse getRealBalanceCoin(List<String> addresses) {
        final Map<String, Coin> balances = new HashMap<String, Coin>();
        for (final String address : addresses) {
            Coin value = this.getRealBalance(address);
            balances.put(address, value);
        }
        final List<String> elements = addresses.stream()
                .map(address -> balances.get(address).toString()).collect(Collectors.toCollection(LinkedList::new));
        return GetBalancesResponse.create(elements, null, 0);
    }
    
    public Coin getRealBalance(String address) {
        List<byte[]> pubKeyHashs = new ArrayList<byte[]>();
        ECKey key = ECKey.fromPublicOnly(HEX.decode(address));
        pubKeyHashs.add(key.getPubKeyHash());
        return this.getRealSystemTokenCoinBalanceByHash(pubKeyHashs);
    }
    
    public Coin getRealSystemTokenCoinBalanceByHash(List<byte[]> pubKeyHashs) {
        return this.getRealBalance(pubKeyHashs, NetworkParameters.BIGNETCOIN_TOKENID);
    }

    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on
     * keys that the wallet contains.
     * 
     * @return The list of candidates.
     */
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
    
    public List<UTXO>
    

    /**
     * Get all the {@link UTXO}'s from the {@link UTXOProvider} based on keys
     * that the wallet contains.
     * @return The list of stored outputs.
     */
    protected List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs) throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        return store.getOpenTransactionOutputs(addresses);
    }
}
