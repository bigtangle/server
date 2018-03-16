/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet;
 
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class WalletWrapper extends Wallet {

    public WalletWrapper(NetworkParameters params) {
        super(params);
    }

    private static final Logger log = LoggerFactory.getLogger(WalletWrapper.class);  
    
    @Override
    public List<TransactionOutput> calculateAllSpendCandidates() {
        lock.lock();
        try {
            List<TransactionOutput> candidates = new ArrayList<TransactionOutput>();
            return candidates;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void completeTx(SendRequest req) throws InsufficientMoneyException {
        lock.lock();
        try {
            List<TransactionOutput> candidates = calculateAllSpendCandidates();
            CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
            CoinSelection bestCoinSelection = selector.select(params.getMaxMoney(), candidates);
            req.tx.getOutput(0).setValue(bestCoinSelection.valueGathered);

            for (TransactionOutput output : bestCoinSelection.gathered) {
                req.tx.addInput(output);
            }
            
            // Now shuffle the outputs to obfuscate which is the change.
            if (req.shuffleOutputs) {
                req.tx.shuffleOutputs();
            }
            
            // Now sign the inputs, thus proving that we are entitled to redeem the connected outputs.
            if (req.signInputs) {
                signTransaction(req);
            }
            
            // Check size.
            final int size = req.tx.unsafeBitcoinSerialize().length;
            if (size > Transaction.MAX_STANDARD_TX_SIZE) {
                throw new ExceededMaxTransactionSize();
            }
            
            req.tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            req.tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
            req.tx.setExchangeRate(req.exchangeRate);
            req.tx.setMemo(req.memo);
            req.completed = true;
            log.info("  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }
}
