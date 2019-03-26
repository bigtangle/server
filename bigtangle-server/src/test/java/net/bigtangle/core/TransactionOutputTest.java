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

package net.bigtangle.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import net.bigtangle.params.MainNetParams;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.testing.TestWithWallet;
import net.bigtangle.wallet.SendRequest;

public class TransactionOutputTest extends TestWithWallet {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    //TODO  new binary @Test
    public void testMultiSigOutputToString() throws Exception {
        sendMoneyToWallet(Coin.COIN);
        ECKey myKey = new ECKey();
        this.wallet.importKey(myKey);

        // Simulate another signatory
        ECKey otherKey = new ECKey();

        // Create multi-sig transaction
        Transaction multiSigTransaction = new Transaction(PARAMS);
        ImmutableList<ECKey> keys = ImmutableList.of(myKey, otherKey);

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, keys);
        multiSigTransaction.addOutput(Coin.COIN, scriptPubKey);

        SendRequest req = SendRequest.forTx(multiSigTransaction);
        this.wallet.completeTx(req,null);
        TransactionOutput multiSigTransactionOutput = multiSigTransaction.getOutput(0);

     //TODO   assertThat(multiSigTransactionOutput.toString(), CoreMatchers.containsString("CHECKMULTISIG"));
    }

    @Test
    public void testP2SHOutputScript() throws Exception {
        String P2SHAddressString = "35b9vsyH1KoFT5a5KtrKusaCcPLkiSo1tU";
        Address P2SHAddress = Address.fromBase58(MainNetParams.get(), P2SHAddressString);
        Script script = ScriptBuilder.createOutputScript(P2SHAddress);
        Transaction tx = new Transaction(MainNetParams.get());
        tx.addOutput(Coin.COIN, script);
        assertEquals(P2SHAddressString, tx.getOutput(0).getAddressFromP2SH(MainNetParams.get()).toString());
    }

    @Test
    public void getAddressTests() throws Exception {
        Transaction tx = new Transaction(MainNetParams.get());
        tx.addOutput(Coin.CENT, ScriptBuilder.createOpReturnScript("hello world!".getBytes()));
        assertNull(tx.getOutput(0).getAddressFromP2SH(PARAMS));
        assertNull(tx.getOutput(0).getAddressFromP2PKHScript(PARAMS));
    }

   
}
