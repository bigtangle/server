/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Utils.HEX;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Message;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ScriptException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.VerificationException;
import net.bigtangle.core.TransactionConfidence.ConfidenceType;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.store.BlockGraph;
import net.bigtangle.testing.FakeTxBuilder;

/**
 * Just check the Transaction.verify() method. Most methods that have
 * complicated logic in Transaction are tested elsewhere, e.g. signing and
 * hashing are well exercised by the wallet tests, the full block chain tests
 * and so on. The verify method is also exercised by the full block chain tests,
 * but it can also be used by API users alone, so we make sure to cover it here
 * as well.
 */
public class TransactionTest {
    private static final NetworkParameters PARAMS = UnitTestParams.get();
    private static final Address ADDRESS = new ECKey().toAddress(PARAMS);

    private Transaction tx;

    @Before
    public void setUp() throws Exception {
        Context context = new Context(PARAMS);
        tx = FakeTxBuilder.createFakeTx(PARAMS);
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void emptyOutputs() throws Exception {
        tx.clearOutputs();
        tx.verify();
    }

    @Test(expected = VerificationException.EmptyInputsOrOutputs.class)
    public void emptyInputs() throws Exception {
        tx.clearInputs();
        tx.verify();
    }

    @Test(expected = VerificationException.LargerThanMaxBlockSize.class)
    public void tooHuge() throws Exception {
        tx.getInput(0).setScriptBytes(new byte[Block.MAX_BLOCK_SIZE]);
        tx.verify();
    }

    @Test(expected = VerificationException.DuplicatedOutPoint.class)
    public void duplicateOutPoint() throws Exception {
        TransactionInput input = tx.getInput(0);
        input.setScriptBytes(new byte[1]);
        tx.addInput(input.duplicateDetached());
        tx.verify();
    }

    @Test(expected = VerificationException.NegativeValueOutput.class)
    public void negativeOutput() throws Exception {
        tx.getOutput(0).setValue(Coin.NEGATIVE_SATOSHI);
        tx.verify();
    }

 
    @Test(expected = VerificationException.UnexpectedCoinbaseInput.class)
    public void coinbaseInputInNonCoinbaseTX() throws Exception {
        tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL, new ScriptBuilder().data(new byte[10]).build());
        tx.verify();
    }

    @Test(expected = VerificationException.CoinbaseScriptSizeOutOfRange.class)
    public void coinbaseScriptSigTooSmall() throws Exception {
        tx.clearInputs();
        tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL, new ScriptBuilder().build());
        tx.verify();
    }

    @Test(expected = VerificationException.CoinbaseScriptSizeOutOfRange.class)
    public void coinbaseScriptSigTooLarge() throws Exception {
        tx.clearInputs();
        TransactionInput input = tx.addInput(Sha256Hash.ZERO_HASH, 0xFFFFFFFFL,
                new ScriptBuilder().data(new byte[99]).build());
        assertEquals(101, input.getScriptBytes().length);
        tx.verify();
    }

    @Test
    public void testOptimalEncodingMessageSize() {
        Transaction tx = new Transaction(PARAMS);

        int length = tx.length;

        // add basic transaction input, check the length
        tx.addOutput(new TransactionOutput(PARAMS, null, Coin.COIN, ADDRESS));
        length += getCombinedLength(tx.getOutputs());

        // add basic output, check the length
        length += getCombinedLength(tx.getInputs());

        // optimal encoding size should equal the length we just calculated
        assertEquals(tx.getOptimalEncodingMessageSize(), length);
    }

    private int getCombinedLength(List<? extends Message> list) {
        int sumOfAllMsgSizes = 0;
        for (Message m : list) {
            sumOfAllMsgSizes += m.getMessageSize() + 1;
        }
        return sumOfAllMsgSizes;
    }

    

    @Test
    public void testCLTVPaymentChannelTransactionSpending() {
        BigInteger time = BigInteger.valueOf(20);

        ECKey from = new ECKey(), to = new ECKey(), incorrect = new ECKey();
        Script outputScript = ScriptBuilder.createCLTVPaymentChannelOutput(time, from, to);

        Transaction tx = new Transaction(PARAMS);
        tx.addInput(new TransactionInput(PARAMS, tx, new byte[] {}));
        tx.getInput(0).setSequenceNumber(0);
        tx.setLockTime(time.subtract(BigInteger.ONE).longValue());
        TransactionSignature fromSig = tx.calculateSignature(0, from, outputScript, Transaction.SigHash.SINGLE, false);
        TransactionSignature toSig = tx.calculateSignature(0, to, outputScript, Transaction.SigHash.SINGLE, false);
        TransactionSignature incorrectSig = tx.calculateSignature(0, incorrect, outputScript,
                Transaction.SigHash.SINGLE, false);
        Script scriptSig = ScriptBuilder.createCLTVPaymentChannelInput(fromSig, toSig);
        Script refundSig = ScriptBuilder.createCLTVPaymentChannelRefund(fromSig);
        Script invalidScriptSig1 = ScriptBuilder.createCLTVPaymentChannelInput(fromSig, incorrectSig);
        Script invalidScriptSig2 = ScriptBuilder.createCLTVPaymentChannelInput(incorrectSig, toSig);

        try {
            scriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
        } catch (ScriptException e) {
            e.printStackTrace();
            fail("Settle transaction failed to correctly spend the payment channel");
        }

        try {
            refundSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Refund passed before expiry");
        } catch (ScriptException e) {
        }
        try {
            invalidScriptSig1.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig 1 passed");
        } catch (ScriptException e) {
        }
        try {
            invalidScriptSig2.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig 2 passed");
        } catch (ScriptException e) {
        }
    }

    @Test
    public void testCLTVPaymentChannelTransactionRefund() {
        BigInteger time = BigInteger.valueOf(20);

        ECKey from = new ECKey(), to = new ECKey(), incorrect = new ECKey();
        Script outputScript = ScriptBuilder.createCLTVPaymentChannelOutput(time, from, to);

        Transaction tx = new Transaction(PARAMS);
        tx.addInput(new TransactionInput(PARAMS, tx, new byte[] {}));
        tx.getInput(0).setSequenceNumber(0);
        tx.setLockTime(time.add(BigInteger.ONE).longValue());
        TransactionSignature fromSig = tx.calculateSignature(0, from, outputScript, Transaction.SigHash.SINGLE, false);
        TransactionSignature incorrectSig = tx.calculateSignature(0, incorrect, outputScript,
                Transaction.SigHash.SINGLE, false);
        Script scriptSig = ScriptBuilder.createCLTVPaymentChannelRefund(fromSig);
        Script invalidScriptSig = ScriptBuilder.createCLTVPaymentChannelRefund(incorrectSig);

        try {
            scriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
        } catch (ScriptException e) {
            e.printStackTrace();
            fail("Refund failed to correctly spend the payment channel");
        }

        try {
            invalidScriptSig.correctlySpends(tx, 0, outputScript, Script.ALL_VERIFY_FLAGS);
            fail("Invalid sig passed");
        } catch (ScriptException e) {
        }
    }

    @Test
    public void testToStringWhenIteratingOverAnInputCatchesAnException() {
        Transaction tx = FakeTxBuilder.createFakeTx(PARAMS);
        TransactionInput ti = new TransactionInput(PARAMS, tx, new byte[0]) {
            @Override
            public Script getScriptSig() throws ScriptException {
                throw new ScriptException("");
            }
        };

        tx.addInput(ti);
        assertEquals(tx.toString().contains("[exception: "), true);
    }

    @Test
    public void testToStringWhenThereAreZeroInputs() {
        Transaction tx = new Transaction(PARAMS);
        assertEquals(tx.toString().contains("No inputs!"), true);
    }

   
    @Test(expected = ScriptException.class)
    public void testAddSignedInputThrowsExceptionWhenScriptIsNotToRawPubKeyAndIsNotToAddress() {
        ECKey key = new ECKey();
        Address addr = key.toAddress(PARAMS);
        Transaction fakeTx = FakeTxBuilder.createFakeTx(PARAMS, Coin.COIN, addr);

        Transaction tx = new Transaction(PARAMS);
        tx.addOutput(fakeTx.getOutput(0));

        Script script = ScriptBuilder.createOpReturnScript(new byte[0]);

        tx.addSignedInput(fakeTx.getOutput(0).getOutPointFor(), script, key);
    }

 
   // @Test
    public void testCoinbaseHeightCheck() throws VerificationException {
        // Coinbase transaction from block 300,000
        final byte[] transactionBytes = HEX.decode(
                "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4803e09304062f503253482f0403c86d53087ceca141295a00002e522cfabe6d6d7561cf262313da1144026c8f7a43e3899c44f6145f39a36507d36679a8b7006104000000000000000000000001c8704095000000001976a91480ad90d403581fa3bf46086a91b2d9d4125db6c188ac00000000");
        final int height = 300000;
        final Transaction transaction = PARAMS.getDefaultSerializer().makeTransaction(transactionBytes);
        transaction.checkCoinBaseHeight(height);
    }
 

    @Test
    public void optInFullRBF() {
        // a standard transaction as wallets would create
        Transaction tx = FakeTxBuilder.createFakeTx(PARAMS);
        assertFalse(tx.isOptInFullRBF());

        tx.getInputs().get(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 2);
        assertTrue(tx.isOptInFullRBF());
    }

    /**
     * Ensure that hashForSignature() doesn't modify a transaction's data, which
     * could wreak multithreading havoc.
     */
   // @Test
    public void testHashForSignatureThreadSafety() {
        Block genesis = UnitTestParams.get().getGenesisBlock();
        Block block1 = BlockForTest.createNextBlock(genesis,new ECKey().toAddress(UnitTestParams.get()),
                genesis.getTransactions().get(0).getOutput(0).getOutPointFor(), genesis.getHash());

        final Transaction tx = block1.getTransactions().get(1);
        final String txHash = tx.getHashAsString();
        final String txNormalizedHash = tx.hashForSignature(0, new byte[0], Transaction.SigHash.ALL.byteValue())
                .toString();

        for (int i = 0; i < 100; i++) {
            // ensure the transaction object itself was not modified; if it was,
            // the hash will change
            assertEquals(txHash, tx.getHashAsString());
            new Thread() {
                public void run() {
                    assertEquals(txNormalizedHash,
                            tx.hashForSignature(0, new byte[0], Transaction.SigHash.ALL.byteValue()).toString());
                }
            };
        }
    }
}
