/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.examples;

import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import com.bignetcoin.store.BlockGraph;
import com.bignetcoin.store.MemoryBlockStore;
import com.bignetcoin.store.PeerGroup;

import java.io.File;

/**
 * RefreshWallet loads a wallet, then processes the block chain to update the transaction pools within it.
 * To get a test wallet you can use wallet-tool from the tools subproject.
 */
public class RefreshWallet {
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        Wallet wallet = Wallet.loadFromFile(file);
        System.out.println(wallet.toString());

        // Set up the components and link them together.
        final NetworkParameters params = TestNet3Params.get();
        BlockStore blockStore = new MemoryBlockStore(params);
        BlockGraph chain = new BlockGraph(params,   blockStore);

        final PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.startAsync();

        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public synchronized void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                System.out.println("\nReceived tx " + tx.getHashAsString());
                System.out.println(tx.toString());
            }
        });

        // Now download and process the block chain.
        peerGroup.downloadBlockChain();
        peerGroup.stopAsync();
        wallet.saveToFile(file);
        System.out.println("\nDone!\n");
        System.out.println(wallet.toString());
    }
}
