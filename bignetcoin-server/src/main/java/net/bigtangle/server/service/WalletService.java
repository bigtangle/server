/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProviderException;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetBalancesResponse;
import net.bigtangle.server.response.GetOutputsResponse;
import net.bigtangle.server.transaction.FreeStandingTransactionOutput;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Wallet;

@Service
public class WalletService {

    public AbstractResponse getAccountBalanceInfo(List<byte[]> pubKeyHashs) {
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                false);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            if (!freeStandingTransactionOutput.getUTXO().isSpent()) {
                outputs.add(freeStandingTransactionOutput.getUTXO());
            }
        }
        Map<String, Coin> value = new HashMap<String, Coin>();
        for (TransactionOutput output : transactionOutputs) {
            if (value.containsKey(output.getValue().getTokenHex())) {
                value.put(output.getValue().getTokenHex(), value.get(output.getValue().getTokenHex()))
                        .add(output.getValue());
            } else {
                value.put(output.getValue().getTokenHex(), output.getValue());
            }
        }

        List<Coin> tokens = new ArrayList<Coin>();
        for (Map.Entry<String, Coin> entry : value.entrySet()) {
            tokens.add(entry.getValue());
        }
        return GetBalancesResponse.create(tokens, outputs);
    }

//    private void filter(List<TransactionOutput> candidates, byte[] tokenid) {
//        for (Iterator<TransactionOutput> iterator = candidates.iterator(); iterator.hasNext();) {
//            TransactionOutput transactionOutput = iterator.next();
//            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
//            if (freeStandingTransactionOutput.getUTXO().getTokenid() != tokenid) {
//                iterator.remove();
//            }
//        }
//    }

    public Wallet makeWallat(ECKey ecKey) {
        KeyChainGroup group = new KeyChainGroup(networkParameters);
        group.importKeys(ecKey);
        return new Wallet(networkParameters, group);
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
            int chainHeight = 10;
            for (UTXO output : getStoredOutputsFromUTXOProvider(pubKeyHashs)) {
                if (output.isSpent())
                    continue;
                boolean coinbase = output.isCoinbase();
                long depth = chainHeight - output.getHeight() + 1;
                // Do not try and spend coinbases that were mined too recently,
                // the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= networkParameters.getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(networkParameters, output, chainHeight));
                    // System.out.println(output.getHeight());
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        return candidates;
    }
    
    public LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(List<byte[]> pubKeyHashs, byte[] tokenid,
            boolean excludeImmatureCoinbases) {
        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
        try {
            int chainHeight = 10;
            for (UTXO output : getStoredOutputsFromUTXOProvider(pubKeyHashs, tokenid)) {
                if (output.isSpent())
                    continue;
                boolean coinbase = output.isCoinbase();
                long depth = chainHeight - output.getHeight() + 1;
                // Do not try and spend coinbases that were mined too recently,
                // the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= networkParameters.getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(networkParameters, output, chainHeight));
                    // System.out.println(output.getHeight());
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        return candidates;
    }

    private List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs, byte[] tokenid) throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        List<UTXO> list = store.getOpenTransactionOutputs(addresses, tokenid);
        return list;
    }

    private List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs) throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        List<UTXO> list = store.getOpenTransactionOutputs(addresses);
        return list;
    }

    public AbstractResponse getAccountOutputs(List<byte[]> pubKeyHashs) {
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                false);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }
        return GetOutputsResponse.create(outputs);
    }

    public AbstractResponse getAccountOutputsWithToken(byte[] pubKey, byte[] tokenid) {
        List<byte[]> pubKeyHashs = new ArrayList<byte[]>();
        pubKeyHashs.add(pubKey);
        
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                false);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }
        return GetOutputsResponse.create(outputs);
    }
}
