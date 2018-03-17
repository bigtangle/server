/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class WalletWrapper extends Wallet {

    public WalletWrapper(NetworkParameters params) {
        super(params);
    }

    private static final Logger log = LoggerFactory.getLogger(WalletWrapper.class);  
    
    @SuppressWarnings("unchecked")
    @Override
    public List<TransactionOutput> calculateAllSpendCandidates() {
        lock.lock();
        try {
            ECKey toKey = this.currentReceiveKey();
            String response = OkHttp3Util.post("http://localhost:14265/getOutputs", toKey.getPubKeyHash());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            List<UTXO> outputs = new ArrayList<UTXO>();
            for (Map<String, Object> map : (List<Map<String, Object>>) data.get("outputs")) {
                UTXO utxo = MapToBeanMapperUtil.parseUTXO(map);
                outputs.add(utxo);
            }
            List<TransactionOutput> candidates = new ArrayList<TransactionOutput>();
            for (UTXO output : outputs) {
                candidates.add(new FreeStandingTransactionOutput(this.params, output, 0)); // TODO jiang unkown
            }
            return candidates;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<TransactionOutput>();
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
