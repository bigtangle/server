/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.examples;

import java.net.InetAddress;

import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.LevelDBFullPrunedBlockStore;

public class LevelDB {
    public static void main(String[] args) throws Exception {
        /*
         * This is just a test runner that will download blockchain till block
         * 390000 then exit.
         */
        FullPrunedBlockStore store = new LevelDBFullPrunedBlockStore(
                MainNetParams.get(), args[0], 1000, 100 * 1024 * 1024l,
                10 * 1024 * 1024, 100000, true, 390000);

        FullPrunedBlockGraph vChain = new FullPrunedBlockGraph(
                MainNetParams.get(), store);
        vChain.setRunScripts(false);

        PeerGroup vPeerGroup = new PeerGroup(MainNetParams.get(), vChain);
        vPeerGroup.setUseLocalhostPeerWhenPossible(true);
        vPeerGroup.addAddress(InetAddress.getLocalHost());

        vPeerGroup.start();
        vPeerGroup.downloadBlockChain();
    }
}
