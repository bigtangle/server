/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProvider;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

/**
 * <p>
 * A TransactionService provides service for transactions that send and receive
 * value from user keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * protocol.
 * </p>
 */
@Service
public class TransactionService {
    @Autowired
    protected FullPrunedBlockStore store;

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    @Autowired
    protected NetworkParameters networkParameters;

    public Coin getBalance(List<byte[]> pubKeyHashs) {
        return getBalance(BalanceType.AVAILABLE, pubKeyHashs);
    }

    /**
     * Returns the balance of this wallet as calculated by the provided
     * balanceType.
     */
    public Coin getBalance(BalanceType balanceType, List<byte[]> pubKeyHashs) {

        if (balanceType == BalanceType.AVAILABLE || balanceType == BalanceType.AVAILABLE_SPENDABLE) {
            List<TransactionOutput> candidates = calculateAllSpendCandidates(pubKeyHashs, true,
                    balanceType == BalanceType.AVAILABLE_SPENDABLE);
            CoinSelection selection = coinSelector.select(NetworkParameters.MAX_MONEY, candidates);
            return selection.valueGathered;
        } else if (balanceType == BalanceType.ESTIMATED || balanceType == BalanceType.ESTIMATED_SPENDABLE) {
            List<TransactionOutput> all = calculateAllSpendCandidates(pubKeyHashs, false,
                    balanceType == BalanceType.ESTIMATED_SPENDABLE);
            Coin value = Coin.ZERO;
            for (TransactionOutput out : all)
                value = value.add(out.getValue());
            return value;
        } else {
            throw new AssertionError("Unknown balance type"); // Unreachable.
        }

    }

    /**
     * Returns a list of all outputs that are being tracked by this wallet
     * either from the {@link UTXOProvider} (in this case the existence or not
     * of private keys is ignored), or the wallets internal storage (the
     * default) taking into account the flags.
     *
     * @param excludeImmatureCoinbases
     *            Whether to ignore coinbase outputs that we will be able to
     *            spend in future once they mature.
     * @param excludeUnsignable
     *            Whether to ignore outputs that we are tracking but don't have
     *            the keys to sign for.
     */
    public List<TransactionOutput> calculateAllSpendCandidates(List<byte[]> pubKeyHashs,
            boolean excludeImmatureCoinbases, boolean excludeUnsignable) {

        List<TransactionOutput> candidates = calculateAllSpendCandidatesFromUTXOProvider(pubKeyHashs,
                excludeImmatureCoinbases);

        return candidates;

    }

    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on
     * keys that the wallet contains.
     * 
     * @return The list of candidates.
     */
    protected LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(List<byte[]> pubKeyHashs,
            boolean excludeImmatureCoinbases) {

        // UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO
        // provider has been set");
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
        // We need to handle the pending transactions that we know about.
        /*
         * for (Transaction tx : pending.values()) { // Remove the spent
         * outputs. for (TransactionInput input : tx.getInputs()) { if
         * (input.getConnectedOutput().isMine(this)) {
         * candidates.remove(input.getConnectedOutput()); } } // Add change
         * outputs. Do not try and spend coinbases that were mined too recently,
         * the protocol forbids it. if (!excludeImmatureCoinbases ||
         * tx.isMature()) { for (TransactionOutput output : tx.getOutputs()) {
         * if (output.isAvailableForSpending() && output.isMine(this)) {
         * candidates.add(output); } } } }
         */
        return candidates;
    }

    /**
     * Get all the {@link UTXO}'s from the {@link UTXOProvider} based on keys
     * that the wallet contains.
     * 
     * @return The list of stored outputs.
     */
    protected List<UTXO> getStoredOutputsFromUTXOProvider(List<byte[]> pubKeyHashs) throws UTXOProviderException {
        // UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO
        // provider has been s
        // List<ECKey> keys = getImportedKeys();
        // keys.addAll(getActiveKeyChain().getLeafKeys());
        List<Address> addresses = new ArrayList<Address>();
        for (byte[] key : pubKeyHashs) {
            Address address = new Address(networkParameters, key);
            addresses.add(address);
        }
        return store.getOpenTransactionOutputs(addresses);

    }

    private class FreeStandingTransactionOutput extends TransactionOutput {
        private UTXO output;
        private int chainHeight;

        /**
         * Construct a free standing Transaction Output.
         * 
         * @param params
         *            The network parameters.
         * @param output
         *            The stored output (free standing).
         */
        public FreeStandingTransactionOutput(NetworkParameters params, UTXO output, int chainHeight) {
            super(params, null, output.getValue(), output.getScript().getProgram());
            this.output = output;
            this.chainHeight = chainHeight;
        }

        /**
         * Get the {@link UTXO}.
         * 
         * @return The stored output.
         */
        public UTXO getUTXO() {
            return output;
        }

        /**
         * Get the depth withing the chain of the parent tx, depth is 1 if it
         * the output height is the height of the latest block.
         * 
         * @return The depth.
         */
        @Override
        public int getParentTransactionDepthInBlocks() {
            return chainHeight - output.getHeight() + 1;
        }

        @Override
        public int getIndex() {
            return (int) output.getIndex();
        }

        @Override
        public Sha256Hash getParentTransactionHash() {
            return output.getHash();
        }
    }
}
