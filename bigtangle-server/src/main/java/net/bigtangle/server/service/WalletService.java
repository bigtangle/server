/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.Token;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.core.http.server.resp.GetOutputsResponse;
import net.bigtangle.core.http.server.resp.OutputsDetailsResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Wallet;

@Service
public class WalletService {

    public AbstractResponse getAccountBalanceInfo(Set<byte[]> pubKeyHashs) throws BlockStoreException {
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                false);
        Map<String, Coin> values = new HashMap<String, Coin>();

        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            UTXO output = freeStandingTransactionOutput.getUTXO();
            outputs.add(output);
            Coin v = output.getValue();
            if (values.containsKey(v.getTokenHex())) {
                Coin nv = values.get(v.getTokenHex()).add(v);
                values.put(output.getValue().getTokenHex(), nv);
            } else {
                values.put(v.getTokenHex(), v);
            }
        }
        List<Coin> tokens = new ArrayList<Coin>();
        for (Map.Entry<String, Coin> entry : values.entrySet()) {
            tokens.add(entry.getValue());
            HashMap<String, Object> r = new HashMap<String, Object>();
            r.put("value", entry.getValue().getValue());
            r.put("tokenHex", entry.getValue().getTokenHex());
            r.put("tokenName", entry.getValue().getTokenHex());
        }

        return GetBalancesResponse.create(tokens, outputs, getTokename(outputs));
    }

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

    public LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(Set<byte[]> pubKeyHashs,
            boolean excludeImmatureCoinbases) {
        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
        try {
           
            for (UTXO output : getStoredOutputsFromUTXOProvider(pubKeyHashs)) {
                if (output.isSpent() || !output.isConfirmed())
                    continue;
                candidates.add(new FreeStandingTransactionOutput(networkParameters, output));

            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        return candidates;
    }

    public LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(List<byte[]> pubKeyHashs,
            byte[] tokenid, boolean excludeImmatureCoinbases) {
        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
        try {
             for (UTXO output : getStoredOutputsFromUTXOProvider(pubKeyHashs, tokenid)) {
                if (output.isSpent() || !output.isConfirmed())
                    continue;
                candidates.add(new FreeStandingTransactionOutput(networkParameters, output));
            }
        } catch (

        UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        return candidates;
    }

    private List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs, byte[] tokenid)
            throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        List<UTXO> list = store.getOpenTransactionOutputs(addresses, tokenid);
        return list;
    }

    private List<UTXO> getStoredOutputsFromUTXOProvider(Set<byte[]> pubKeyHashs) throws UTXOProviderException {
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        List<UTXO> list = store.getOpenTransactionOutputs(addresses);
        return list;
    }

    public AbstractResponse getAccountOutputs(Set<byte[]> pubKeyHashs) throws BlockStoreException {
        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                false);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }
        return GetOutputsResponse.create(outputs, getTokename(outputs));
    }

    public AbstractResponse getOutputsHistory(String fromaddress, String toaddress, Long starttime, Long endtime)
            throws Exception {
        List<UTXO> outputs = this.store.getOutputsHistory(fromaddress, toaddress, starttime, endtime);

        return GetOutputsResponse.create(outputs, getTokename(outputs));
    }

    public AbstractResponse getAccountOutputsWithToken(byte[] pubKey, byte[] tokenid) throws BlockStoreException {
        List<byte[]> pubKeyHashs = new ArrayList<byte[]>();
        pubKeyHashs.add(pubKey);

        List<UTXO> outputs = new ArrayList<UTXO>();
        List<TransactionOutput> transactionOutputs = this.calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                tokenid, false);
        for (TransactionOutput transactionOutput : transactionOutputs) {
            FreeStandingTransactionOutput freeStandingTransactionOutput = (FreeStandingTransactionOutput) transactionOutput;
            outputs.add(freeStandingTransactionOutput.getUTXO());
        }
        return GetOutputsResponse.create(outputs, getTokename(outputs));
    }

    public AbstractResponse getOutputsWithHexStr(String hexStr) throws BlockStoreException {
        UTXO output = getStoredOutputsWithHexStr(hexStr);
        return OutputsDetailsResponse.create(output);
    }

    public AbstractResponse getOutputsMultiList(String hexStr, int index) throws BlockStoreException {
        List<OutputsMulti> outputsMultis = this.store.queryOutputsMultiByHashAndIndex(Utils.HEX.decode(hexStr), index);

        return OutputsDetailsResponse.create(outputsMultis);
    }

    private UTXO getStoredOutputsWithHexStr(String hexStr) throws BlockStoreException {
        String[] strs = hexStr.split(":");
        byte[] hash = Utils.HEX.decode(strs[0]);
        long outputindex = Long.parseLong(strs[1]);
        UTXO utxo = store.getOutputsWithHexStr(hash, outputindex);
        return utxo;
    }

    public Map<String, Token> getTokename(List<UTXO> outxos) throws BlockStoreException {
        Set<String> tokenids = new HashSet<String>();
        for (UTXO d : outxos) {
            tokenids.add(d.getTokenId());

        }
        Map<String, Token> re = new HashMap<String, Token>();
        List<Token> tokens = store.getTokensList(tokenids);
        for (Token t : tokens) {
            re.put(t.getTokenid(), t);
        }
         
        return re;
    }
}
