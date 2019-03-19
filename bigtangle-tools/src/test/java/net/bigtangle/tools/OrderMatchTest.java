package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderMatchTest extends AbstractIntegrationTest {

	@Test
	public void buy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell order for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

	}

	@Test
	public void sell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

		// Open sell order for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	}

	@Test
	public void multiLevelBuy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 1001, 100, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 999, 50, addedBlocks);

	}

	@Test
	public void multiLevelSell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 100, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 50, addedBlocks);

	}

	@Test
	public void partialBuy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
	}

	@Test
	public void partialSell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	}

	@Test
	public void partialBidFill() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);
	}

	@Test
	public void partialAskFill() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

	}

	@Test
	public void cancel() throws Exception {
		@SuppressWarnings({ "deprecation", "unused" })
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Cancel
		makeAndConfirmCancelOp(sell, testKey, addedBlocks);
	}

	@Test
	public void cancelTwoStep() throws Exception {
		@SuppressWarnings({ "deprecation", "unused" })
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	}

	@Test
	public void partialCancel() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

		// Cancel sell
		makeAndConfirmCancelOp(sell, testKey, addedBlocks);

	}

	@Test
	public void ineffectiveCancel() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Open sell orders for test tokens
		Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

		// Cancel sell
		makeAndConfirmCancelOp(sell, testKey, addedBlocks);
	}

	@Test
	public void testValidFromTime() throws Exception {
		final int waitTime = 5000;

		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = walletKeys.get(8);
		;
		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		resetAndMakeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		long sellAmount = (long) 100;
		Block block = null;
		Transaction tx = new Transaction(networkParameters);
		OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
				testKey.getPubKey(), null, System.currentTimeMillis() + waitTime, Side.SELL,
				testKey.toAddress(networkParameters).toBase58());
		tx.setData(info.toByteArray());

		// Burn tokens to sell
		Coin amount = Coin.valueOf(sellAmount, testTokenId);
		List<UTXO> outputs = getBalance(false, testKey).stream()
				.filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
				.filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
				0);
		// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
		// amount, testKey));
		tx.addOutput(
				new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
		TransactionInput input = tx.addInput(spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

		TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);

		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

		// Wait until valid
		Thread.sleep(waitTime);
	}

}
