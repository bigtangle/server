/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;



import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.Sha256Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.model.TipsViewModel;

@Service
public class BlockValidator {
    @Autowired
    private   MilestoneService milestoneSevice;

    @Autowired
    private   BlockService blockService; 
    @Autowired
    private   TipsViewModel tipsViewModel;
    
    private int MIN_WEIGHT_MAGNITUDE = 81;

    
    private final AtomicBoolean useFirst = new AtomicBoolean(true);
   
    private final Object cascadeSync = new Object();
    private final Set<Sha256Hash> newSolidBlocksOne = new LinkedHashSet<>();
    private final Set<Sha256Hash> newSolidBlocksTwo = new LinkedHashSet<>();

 
    public void init(boolean testnet,int MAINNET_MWM, int TESTNET_MWM) {
        if(testnet) {
            MIN_WEIGHT_MAGNITUDE = TESTNET_MWM;
        } else {
            MIN_WEIGHT_MAGNITUDE = MAINNET_MWM;
        }
        //lowest allowed MWM encoded in 46 bytes.
        if (MIN_WEIGHT_MAGNITUDE<13){
            MIN_WEIGHT_MAGNITUDE = 13;
        }
 
    }

 
    public int getMinWeightMagnitude() {
        return MIN_WEIGHT_MAGNITUDE;
    }

  

    public boolean checkSolidity(Sha256Hash hash, boolean milestone) throws Exception {
        if(blockService.getBlockEvaluation(hash).isSolid()) {
            return true;
        }
        Set<Sha256Hash> analyzedHashes = new HashSet<>(Collections.singleton(Sha256Hash.ZERO_HASH));
        boolean solid = true;
        final Queue<Sha256Hash> nonAnalyzedBlocks = new LinkedList<>(Collections.singleton(hash));
        Sha256Hash hashPointer;
        while ((hashPointer = nonAnalyzedBlocks.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                BlockEvaluation   blockEvaluation = blockService.getBlockEvaluation( hashPointer);
                if(!blockEvaluation.isSolid()) {
                    Block block = blockService.getBlock(blockEvaluation.getBlockhash());
                    if ( block ==null) {
                        //TODO broken graph download the remote block needed
                        
                        solid = false;
                        break;
                    } else {
                        if (solid) {
                            nonAnalyzedBlocks.offer(block.getPrevBlockHash());
                            nonAnalyzedBlocks.offer(block.getPrevBranchBlockHash());
                        }
                    }
                }
            }
        }
        if (solid) {
            blockService .updateSolidBlocks( analyzedHashes);
        }
        analyzedHashes.clear();
        return solid;
    }

    public void addSolidTransaction(Sha256Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidBlocksOne.add(hash);
            } else {
                newSolidBlocksTwo.add(hash);
            }
        }
    }

 

    public void updateStatus( BlockEvaluation   blockEvaluation) throws Exception {
      //TODO remove from download list  transactionRequester.clearTransactionRequest(transactionViewModel.getHash());
        if(blockService.getSolidApproverBlocks(blockEvaluation.getBlockhash() ).size() == 0) {
            tipsViewModel.addTipHash(blockEvaluation.getBlockhash());
        }
        Block block = blockService.getBlock(blockEvaluation.getBlockhash());
        tipsViewModel.removeTipHash(block.getPrevBlockHash());
        tipsViewModel.removeTipHash(block.getPrevBranchBlockHash());

        if(quickSetSolid(blockEvaluation)) {
            addSolidTransaction(blockEvaluation.getBlockhash());
        }
    }

   

    private boolean quickSetSolid( BlockEvaluation   blockEvaluation) throws Exception {
        if(!blockEvaluation.isSolid()) {
            boolean solid = true;
            /*TODO check prev 
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false;
            } */
            if(solid) {
                blockService.updateSolid(blockEvaluation, true);

                return true;
            }
        }
        //return isSolid();
        return false;
    }

    private boolean checkApproovee( BlockEvaluation blockEvaluation) throws Exception {
        /*TODO check prev 
        if(approovee.getType() == PREFILLED_SLOT) {
            transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        if(approovee.getHash().equals(Sha256Hash.NULL_HASH)) {
            return true;
        }
        */
        return blockEvaluation.isSolid();
    }

}
