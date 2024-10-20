/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.base.ServiceBaseConnect;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

public class FullPrunedBlockGraphTest extends AbstractIntegrationTest {

	@Test
	public void testConnectTransactionalUTXOs() throws Exception {

		// Create block with UTXOs
		Transaction tx1 = createTestTransaction();
		Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1, false);

		// Should exist now
		final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()), store);
		final UTXO utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(block1.getHash()), store);
		assertNotNull(utxo1);
		assertNotNull(utxo2);
		assertFalse(utxo1.isConfirmed());
		assertFalse(utxo2.isConfirmed());
		assertFalse(utxo1.isSpent());
		assertFalse(utxo2.isSpent());
	}

	@Test
	public void testConnectRewardUTXOs() throws Exception {

		// Generate blocks until passing first reward interval
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock1 =  networkParameters.getGenesisBlock() ;

		// Generate mining reward block
		Block rewardBlock1 = makeRewardBlock(networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(),
				rollingBlock1.getHash());

		// Should exist now
		assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
	}

	@Test
	public void testConnectTokenUTXOs() throws Exception {

		ECKey ecKey1 = new ECKey();
		byte[] pubKey = ecKey1.getPubKey();
		// System.out.println(Utils.HEX.encode(pubKey));

		// Generate an eligible issuance
		Sha256Hash firstIssuance;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, pubKey);
			BigInteger amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
					amount, false, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", ecKey1.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently, that's why we
			// need other blocks beforehand.
			Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, ecKey1, null, null);
			firstIssuance = block1.getHash();

			// Should exist now
			store.getTokenConfirmed(block1.getHash()); // Fine as
														// long as it
														// does not
														// throw
			assertFalse(store.getTokenSpent(block1.getHash()));
		}

		// Generate a subsequent issuance
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, pubKey);
			BigInteger amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance, Utils.HEX.encode(pubKey), "Test", "Test", 1,
					1, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			ECKey ecKey = new ECKey();
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", ecKey.getPublicKeyAsHex()));
			ECKey ecKey2 = new ECKey();
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", ecKey2.getPublicKeyAsHex()));

			;

			// This (saveBlock) calls milestoneUpdate currently, that's why we
			// need other blocks beforehand.
			ECKey outKey3 = new ECKey();
			wallet.importKey(ecKey);
			wallet.importKey(ecKey2);
			wallet.importKey(outKey3);
			Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey3, null);

			// block1 = pullBlockDoMultiSign(tokens.getTokenid(), ecKey1, null);
			// Should exist now
			store.getTokenConfirmed(block1.getHash()); // Fine as
														// long as it
														// does not
														// throw
			assertFalse(store.getTokenSpent(block1.getHash()));
		}
	}

 
	@Test
	public void testConfirmTransactionalUTXOs() throws Exception {

		// Create block with UTXOs
		Transaction tx1 = createTestTransaction();
		Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1);

		// Confirm
		makeRewardBlock();

		// Should be confirmed now
		final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(spenderBlock.getHash()), store);
		final UTXO utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(spenderBlock.getHash()), store);
		assertTrue(utxo1.isConfirmed());
		assertTrue(utxo2.isConfirmed());
		assertFalse(utxo1.isSpent());
		assertFalse(utxo2.isSpent());

		// Further manipulations on prev UTXOs
		final UTXO origUTXO = blockService.getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0)
				.getOutput(0).getOutPointFor(networkParameters.getGenesisBlock().getHash()), store);
		assertTrue(origUTXO.isConfirmed());
		assertTrue(origUTXO.isSpent());
		assertEquals(
				store.getTransactionOutputSpender(origUTXO.getBlockHash(), origUTXO.getTxHash(), origUTXO.getIndex())
						.getBlockHash(),
				spenderBlock.getHash());
	}

	@Test
	public void testConfirmRewardUTXOs() throws Exception {

		// Generate blocks until passing first reward interval
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock1 = addFixedBlocks(  networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate mining reward block
		Block rewardBlock1 = makeRewardBlock(networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(),
				rollingBlock1.getHash());

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

		// Further manipulations on prev UTXOs
		assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
		assertTrue(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
		assertEquals(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()), rewardBlock1.getHash());

		// Check the virtual txs too
		Transaction virtualTX = new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.generateVirtualMiningRewardTX(rewardBlock1, store);
		final UTXO utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock1.getHash()), store);
		assertTrue(utxo1.isConfirmed());
		assertFalse(utxo1.isSpent());
	}

	@Test
	public void testConfirmTokenUTXOs() throws Exception {

		// Generate an eligible issuance
		ECKey outKey = new ECKey();
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();

		Coin coinbase = Coin.valueOf(77777L, pubKey);
		BigInteger amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true, 0, networkParameters.getGenesisBlock().getHashAsString());

		tokenInfo.setToken(tokens);

		// This (saveBlock) calls milestoneUpdate currently
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null);
		makeRewardBlock();

		// Should be confirmed now
		assertTrue(store.getTokenConfirmed(block1.getHash()));
		assertFalse(store.getTokenSpent(block1.getHash()));
	}

	 

	@Test
	public void testConfirmOrderMatchUTXOs2() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		// Make the "test" token
		List<Block> addedBlocks = new ArrayList<>();
		makeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
		// Make a buy order for testKey.getPubKey()s
		payBigTo(testKey, Coin.FEE_DEFAULT.getValue(), addedBlocks);
		payBigTo(testKey, Coin.FEE_DEFAULT.getValue(), addedBlocks);
		Block block1 = makeBuyOrder(testKey, Utils.HEX.encode(testKey.getPubKey()), 2, 2, addedBlocks);

		// Make a sell order for testKey.getPubKey()s
		// Open sell order for test tokens
		Block block3 = makeSellOrder(testKey, testTokenId, 2, 2, addedBlocks);

		// Generate matching block
		// Execute order matching
		Block rewardBlock1 = makeOrderExecutionAndReward(addedBlocks);

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

		// Ensure all consumed order records are now spent
		OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
		assertNotNull(order);
		assertTrue(order.isConfirmed());
		assertTrue(order.isSpent());

		OrderRecord order2 = store.getOrder(block3.getHash(), Sha256Hash.ZERO_HASH);
		assertNotNull(order2);
		assertTrue(order2.isConfirmed());
		assertTrue(order2.isSpent());

	}

	@Test
	public void testUnconfirmTransactionalUTXOs() throws Exception {

		// Create block with UTXOs
		Transaction tx11 = createTestTransaction();
		Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx11);

		// Confirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(block.getHash(),
				new HashSet<>(), (long) -1, store);

		// Should be confirmed now
		final UTXO utxo11 = blockService.getUTXO(tx11.getOutput(0).getOutPointFor(block.getHash()), store);
		final UTXO utxo21 = blockService.getUTXO(tx11.getOutput(1).getOutPointFor(block.getHash()), store);
		assertNotNull(utxo11);
		assertNotNull(utxo21);
		assertTrue(utxo11.isConfirmed());
		assertTrue(utxo21.isConfirmed());
		assertFalse(utxo11.isSpent());
		assertFalse(utxo21.isSpent());

		// Unconfirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).unconfirm(block.getHash(),
				new HashSet<>(), store);

		// Should be unconfirmed now
		final UTXO utxo1 = blockService.getUTXO(tx11.getOutput(0).getOutPointFor(block.getHash()), store);
		final UTXO utxo2 = blockService.getUTXO(tx11.getOutput(1).getOutPointFor(block.getHash()), store);
		assertNotNull(utxo1);
		assertNotNull(utxo2);
		assertFalse(utxo1.isConfirmed());
		assertFalse(utxo2.isConfirmed());
		assertFalse(utxo1.isSpent());
		assertFalse(utxo2.isSpent());

		// Further manipulations on prev UTXOs
		final UTXO origUTXO = blockService.getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0)
				.getOutput(0).getOutPointFor(networkParameters.getGenesisBlock().getHash()), store);
		assertTrue(origUTXO.isConfirmed());
		assertFalse(origUTXO.isSpent());
		assertNull(
				store.getTransactionOutputSpender(origUTXO.getBlockHash(), origUTXO.getTxHash(), origUTXO.getIndex()));
	}

	@Test
	public void testUnconfirmRewardUTXOs() throws Exception {
 
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock = addFixedBlocks(  networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate mining reward block
		Block rewardBlock11 = makeRewardBlock(rollingBlock);

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

		// Unconfirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).unconfirm(rewardBlock11.getHash(),
				new HashSet<>(), store);

		// Should be unconfirmed now
		assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

		// Further manipulations on prev UTXOs
		assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
		assertFalse(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
		assertNull(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()));

		// Check the virtual txs too
		Transaction virtualTX = new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.generateVirtualMiningRewardTX(rewardBlock11, store);
		final UTXO utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock11.getHash()), store);
		assertFalse(utxo1.isConfirmed());
		assertFalse(utxo1.isSpent());
	}

	@Test
	public void testUnconfirmTokenUTXOs() throws Exception {

		// Generate an eligible issuance
		ECKey outKey = new ECKey();
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();

		Coin coinbase = Coin.valueOf(77777L, pubKey);
		BigInteger amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true, 0, networkParameters.getGenesisBlock().getHashAsString());

		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// This (saveBlock) calls milestoneUpdate currently
		Block block11 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null);
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(block11.getHash(),
				new HashSet<>(), (long) -1, store);

		// Should be confirmed now
		assertTrue(store.getTokenConfirmed(block11.getHash()));
		assertFalse(store.getTokenSpent(block11.getHash()));

		// Unconfirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).unconfirm(block11.getHash(),
				new HashSet<>(), store);

		// Should be unconfirmed now
		assertFalse(store.getTokenConfirmed(block11.getHash()));
		assertFalse(store.getTokenSpent(block11.getHash()));
	}

 
 

	@Test
	public void testUnconfirmDependentsTransactional() throws Exception {

		// Create blocks with UTXOs
		Transaction tx1 = createTestTransaction();
		Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1, false);
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(block1.getHash(),
				new HashSet<>(), (long) -1, store);
		Block betweenBlock = createAndAddNextBlock(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock());
		Transaction tx2 = createTestTransaction();
		Block block2 = createAndAddNextBlockWithTransaction(betweenBlock, betweenBlock, tx2, false);
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(block2.getHash(),
				new HashSet<>(), (long) -1, store);

		// Should be confirmed now
		assertTrue(getBlockEvaluation(block1.getHash(), store).isConfirmed());
		assertTrue(getBlockEvaluation(block2.getHash(), store).isConfirmed());
		UTXO utxo11 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()), store);
		UTXO utxo21 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(block1.getHash()), store);
		assertNotNull(utxo11);
		assertNotNull(utxo21);
		assertTrue(utxo11.isConfirmed());
		assertTrue(utxo21.isConfirmed());
		assertTrue(utxo11.isSpent() || utxo21.isSpent());
		utxo11 = blockService.getUTXO(tx2.getOutput(0).getOutPointFor(block2.getHash()), store);
		assertNotNull(utxo11);
		assertTrue(utxo11.isConfirmed());
		assertFalse(utxo11.isSpent());

		// Unconfirm first block
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).unconfirmRecursive(block1.getHash(),
				new HashSet<>(), store);

		// Both should be unconfirmed now
		assertFalse(getBlockEvaluation(block1.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(block2.getHash(), store).isConfirmed());

		final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()), store);
		assertNotNull(utxo1);
		assertFalse(utxo1.isConfirmed());
		assertFalse(utxo1.isSpent());

		final UTXO utxo2 = blockService.getUTXO(tx2.getOutput(0).getOutPointFor(block2.getHash()), store);
		assertNotNull(utxo2);
		assertFalse(utxo2.isConfirmed());
		assertFalse(utxo2.isSpent());
	}

	@Test
	public void testUnconfirmDependentsRewardOtherRewards() throws Exception { 
 
		Block rollingBlock =   networkParameters.getGenesisBlock() ;
		Block rewardBlock11 = makeRewardBlock(networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(),
				rollingBlock.getHash());

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

		// Generate second mining reward block
		rollingBlock = rewardBlock11;
	
		Block rewardBlock2 = makeRewardBlock(rewardBlock11.getHash(), rollingBlock.getHash(), rollingBlock.getHash());

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
		assertTrue(store.getRewardSpent(rewardBlock11.getHash()));
		assertTrue(store.getRewardConfirmed(rewardBlock2.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock2.getHash()));

		// Unconfirm rewardBlock11  forward to Unconfirm rewardBlock2
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.unconfirmRecursive(rewardBlock11.getHash(), new HashSet<>(), store);

		// Both should be unconfirmed now
		assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
		assertFalse(store.getRewardConfirmed(rewardBlock2.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock2.getHash()));

	 
	}

	@Test
	public void testUnconfirmDependentsRewardVirtualSpenders() throws Exception {
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock = addFixedBlocks(  networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate mining reward block
		Block rewardBlock = makeRewardBlock(rollingBlock);

		// Should be confirmed now
		assertTrue(store.getRewardConfirmed(rewardBlock.getHash()));
		assertFalse(store.getRewardSpent(rewardBlock.getHash()));

		Block spenderBlock = createAndAddNextBlockWithTransaction(rewardBlock, rewardBlock,
				wallet.feeTransaction(null));

		// Confirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(spenderBlock.getHash(),
				new HashSet<Sha256Hash>(), (long) -1, store);

		// Should be confirmed now

		// Unconfirm reward block
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.unconfirmRecursive(rewardBlock.getHash(), new HashSet<>(), store);

		// Check the virtual txs too
		Transaction virtualTX = new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.generateVirtualMiningRewardTX(rewardBlock, store);
		UTXO utxo3 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock.getHash()), store);
		assertFalse(utxo3.isConfirmed());
		assertFalse(utxo3.isSpent());
		assertNull(store.getTransactionOutputSpender(utxo3.getBlockHash(), utxo3.getTxHash(), utxo3.getIndex()));
	}

	@Test
	public void testUnconfirmDependentsToken() throws Exception {

		ECKey outKey = new ECKey();
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		Sha256Hash firstIssuance;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, pubKey);
			BigInteger amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
					amount, false, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently, that's why we
			// need other blocks beforehand.
			Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);
			firstIssuance = block1.getHash();
			makeRewardBlock();
		}

		// Generate a subsequent issuance
		Sha256Hash subseqIssuance;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, pubKey);
			BigInteger amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance, Utils.HEX.encode(pubKey), "Test", "Test", 1,
					1, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently, that's why we
			// need other blocks beforehand.
			Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);
			subseqIssuance = block1.getHash();
			makeRewardBlock();
		}

		// Confirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(firstIssuance,
				new HashSet<>(), (long) -1, store);
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).confirm(subseqIssuance,
				new HashSet<>(), (long) -1, store);

		// Should be confirmed now
		assertTrue(store.getTokenConfirmed(subseqIssuance));

		// Unconfirm
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).unconfirmRecursive(firstIssuance,
				new HashSet<>(), store);

		// Should be unconfirmed now
		assertFalse(store.getTokenConfirmed(subseqIssuance));

	}

	//@Test
	public void testUnconfirmDependentsOrderVirtualUTXOSpenders() throws Exception {

		ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
				Utils.HEX.decode(testPub));
		ECKey testKey = new ECKey();

		List<Block> addedBlocks = new ArrayList<>();

		// Make test token
		makeTestToken(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();

		// Get current existing token amount
		HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

		// Open sell order for test tokens
		makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

		// Open buy order for test tokens
		makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

		// Execute order matching
		Block rewardBlock = makeOrderExecutionAndReward(addedBlocks);

		// Generate spending block
		Block betweenBlock = makeAndConfirmBlock(addedBlocks, addedBlocks.get(addedBlocks.size() - 2));
		Block utxoSpendingBlock = makeAndConfirmTransaction(genesisKey, new ECKey(), testTokenId, 50, addedBlocks,
				betweenBlock);

		// Unconfirm order matching
		new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
				.unconfirmRecursive(rewardBlock.getHash(), new HashSet<>(), store);

		// Verify the dependent spending block is unconfirmed too
		assertFalse( getBlockEvaluation(utxoSpendingBlock.getHash(), store).isConfirmed());

		// Verify token amount invariance
		assertCurrentTokenAmountEquals(origTokenAmounts);
	}
}