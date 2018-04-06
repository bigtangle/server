/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.bigtangle.examples;

import static net.bigtangle.core.Coin.CENT;
import static net.bigtangle.core.Coin.COIN;
import static net.bigtangle.core.Coin.SATOSHI;

import java.io.File;

import com.bignetcoin.store.Peer;

import net.bigtangle.core.Address;
import net.bigtangle.core.Message;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.listeners.PreMessageReceivedEventListener;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.RegTestParams;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.Wallet;

/**
 * This is a little test app that waits for a coin on a local regtest node, then  generates two transactions that double
 * spend the same output and sends them. It's useful for testing double spend codepaths but is otherwise not something
 * you would normally want to do.
 */
public class DoubleSpend {
    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();
        final RegTestParams params = RegTestParams.get();
        WalletAppKit kit = new WalletAppKit(params, new File("."), "doublespend");
        kit.connectToLocalHost();
        kit.setAutoSave(false);
        kit.startAsync();
        kit.awaitRunning();

        System.out.println(kit.wallet());

        
        Transaction tx1 = kit.wallet().createSend(Address.fromBase58(params, "muYPFNCv7KQEG2ZLM7Z3y96kJnNyXJ53wm"), CENT);
        Transaction tx2 = kit.wallet().createSend(Address.fromBase58(params, "muYPFNCv7KQEG2ZLM7Z3y96kJnNyXJ53wm"), CENT.add(SATOSHI.multiply(10)));
  
 

        Thread.sleep(5000);
        kit.stopAsync();
        kit.awaitTerminated();
    }
}
