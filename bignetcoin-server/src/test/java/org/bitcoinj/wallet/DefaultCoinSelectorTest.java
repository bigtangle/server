/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet;

import org.bitcoinj.core.*;
import org.bitcoinj.params.*;
import org.bitcoinj.testing.*;
import org.junit.*;

import com.bignetcoin.store.AbstractBlockGraph;

import java.net.*;
import java.util.*;

import static com.google.common.base.Preconditions.*;
import static org.bitcoinj.core.Coin.*;
import static org.junit.Assert.*;

public class DefaultCoinSelectorTest extends TestWithWallet {
    private static final NetworkParameters PARAMS = UnitTestParams.get();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Utils.setMockClock(); // Use mock clock
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void selectable() throws Exception {
        Transaction t;
        t = new Transaction(PARAMS);;
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        assertFalse(DefaultCoinSelector.isSelectable(t));
        t.getConfidence().setSource(TransactionConfidence.Source.SELF);
        assertFalse(DefaultCoinSelector.isSelectable(t));
        t.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByName("1.2.3.4")));
        assertFalse(DefaultCoinSelector.isSelectable(t));
        t.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByName("5.6.7.8")));
        assertTrue(DefaultCoinSelector.isSelectable(t));
        t = new Transaction(PARAMS);;
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        assertTrue(DefaultCoinSelector.isSelectable(t));
        t = new Transaction(RegTestParams.get());
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        t.getConfidence().setSource(TransactionConfidence.Source.SELF);
        assertTrue(DefaultCoinSelector.isSelectable(t));
    }

 

 
    @Test
    public void identicalInputs() throws Exception {
        // Add four outputs to a transaction with same value and destination. Select them all.
        Transaction t = new Transaction(PARAMS);;
        java.util.List<TransactionOutput> outputs = Arrays.asList(
            new TransactionOutput(PARAMS, t, Coin.valueOf(30302787,NetworkParameters.BIGNETCOIN_TOKENID), myAddress),
            new TransactionOutput(PARAMS, t, Coin.valueOf(30302787,NetworkParameters.BIGNETCOIN_TOKENID), myAddress),
            new TransactionOutput(PARAMS, t, Coin.valueOf(30302787,NetworkParameters.BIGNETCOIN_TOKENID), myAddress),
            new TransactionOutput(PARAMS, t, Coin.valueOf(30302787,NetworkParameters.BIGNETCOIN_TOKENID), myAddress)
        );
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);

        DefaultCoinSelector selector = new DefaultCoinSelector();
        CoinSelection selection = selector.select(COIN.multiply(2), outputs);

        assertTrue(selection.gathered.size() == 4);
    }
}
