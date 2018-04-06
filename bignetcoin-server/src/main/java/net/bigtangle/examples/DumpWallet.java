/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.examples;

import java.io.File;

import net.bigtangle.wallet.Wallet;

/**
 * DumpWallet loads a serialized wallet and prints information about what it contains.
 */
public class DumpWallet {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java DumpWallet <filename>");
            return;
        }

        Wallet wallet = Wallet.loadFromFile(new File(args[0]));
        System.out.println(wallet.toString());
    }
}
