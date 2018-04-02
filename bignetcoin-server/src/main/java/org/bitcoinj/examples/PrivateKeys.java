/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.examples;

import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.Wallet;

import com.bignetcoin.store.BlockGraph;
import com.bignetcoin.store.MemoryBlockStore;
import com.bignetcoin.store.PeerGroup;

import java.math.BigInteger;
import java.net.InetAddress;

/**
 * This example shows how to solve the challenge Hal posted here:<p>
 *
 * <a href="http://www.bitcoin.org/smf/index.php?topic=3638.0">http://www.bitcoin.org/smf/index.php?topic=3638
 * .0</a><p>
 *
 * in which a private key with some coins associated with it is published. The goal is to import the private key,
 * claim the coins and then send them to a different address.
 */
public class PrivateKeys {
    public static void main(String[] args) throws Exception {
        // TODO: Assumes main network not testnet. Make it selectable.
        NetworkParameters params = MainNetParams.get();
        try {
            // Decode the private key from Satoshis Base58 variant. If 51 characters long then it's from Bitcoins
            // dumpprivkey command and includes a version byte and checksum, or if 52 characters long then it has 
            // compressed pub key. Otherwise assume it's a raw key.
            ECKey key;
            if (args[0].length() == 51 || args[0].length() == 52) {
                DumpedPrivateKey dumpedPrivateKey = DumpedPrivateKey.fromBase58(params, args[0]);
                key = dumpedPrivateKey.getKey();
            } else {
                BigInteger privKey = Base58.decodeToBigInteger(args[0]);
                key = ECKey.fromPrivate(privKey);
            }
            System.out.println("Address from private key is: " + key.toAddress(params).toString());
            // And the address ...
            Address destination = Address.fromBase58(params, args[1]);

            // Import the private key to a fresh wallet.
            Wallet wallet = new Wallet(params);
            wallet.importKey(key);

            // Find the transactions that involve those coins.
            final MemoryBlockStore blockStore = new MemoryBlockStore(params);
            BlockGraph chain = new BlockGraph(params,  blockStore);

            final PeerGroup peerGroup = new PeerGroup(params, chain);
            peerGroup.addAddress(new PeerAddress(InetAddress.getLocalHost()));
            peerGroup.startAsync();
            peerGroup.downloadBlockChain();
            peerGroup.stopAsync();

            // And take them!
         
          //  wallet.sendCoins(peerGroup, destination, wallet.getBalance());
            // Wait a few seconds to let the packets flush out to the network (ugly).
            Thread.sleep(5000);
            System.exit(0);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("First arg should be private key in Base58 format. Second argument should be address " +
                    "to send to.");
        }
    }
}
