/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.ScriptException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.CoinbaseDisallowedException;
import net.bigtangle.core.exception.VerificationException.DifficultyConsensusInheritanceException;
import net.bigtangle.core.exception.VerificationException.GenesisBlockDisallowedException;
import net.bigtangle.core.exception.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.exception.VerificationException.InvalidDependencyException;
import net.bigtangle.core.exception.VerificationException.InvalidOrderException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionException;
import net.bigtangle.core.exception.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.exception.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.exception.VerificationException.NegativeValueOutput;
import net.bigtangle.core.exception.VerificationException.NotCoinbaseException;
import net.bigtangle.core.exception.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.exception.VerificationException.ProofOfWorkException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeReversionException;
import net.bigtangle.core.exception.VerificationException.TimeTravelerException;
import net.bigtangle.core.exception.VerificationException.TransactionOutputsDisallowedException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.service.ServiceBase;
import net.bigtangle.utils.Json;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

public class ValidatorServiceTest extends AbstractIntegrationTest {

	@Test
	public void testVerificationFutureTimestamp() throws Exception {

		Pair<BlockWrap, BlockWrap> tipsToApprove = tipsService.getValidatedBlockPair(store);
		Block r1 = tipsToApprove.getLeft().getBlock();
		Block r2 = tipsToApprove.getRight().getBlock();
		Block b = r2.createNextBlock(r1);
		b.setTime(1887836800); //
		b.solve();
		try {
			blockService.saveBlock(b, store);
			fail();
		} catch (TimeTravelerException e) {
		}
	}

	@Test
	public void testAdjustTimestamp() throws Exception {

		Pair<BlockWrap, BlockWrap> tipsToApprove = tipsService.getValidatedBlockPair(store);
		Block r1 = tipsToApprove.getLeft().getBlock();
		Block r2 = tipsToApprove.getRight().getBlock();
		Block b = r2.createNextBlock(r1);
		b.setTime(1567836800); //
		b.addTransaction(wallet.feeTransaction(null));
		b.solve();
		blockService.adjustPrototype(b, store);
		blockService.saveBlock(b, store);
	}

	@Test
	public void testVerificationIncorrectPoW() throws Exception {

		Pair<BlockWrap, BlockWrap> tipsToApprove = tipsService.getValidatedBlockPair(store);
		Block r1 = tipsToApprove.getLeft().getBlock();
		Block r2 = tipsToApprove.getRight().getBlock();
		Block b = r2.createNextBlock(r1);
		for (int i = 0; i < 300; i++) {
			b.setNonce(i);
			try {
				b.verifyHeader();
			} catch (ProofOfWorkException e) {
				break;
			}
		}
		try {
			blockService.saveBlock(b, store);
			fail();
		} catch (ProofOfWorkException e) {
		}
	}

	@Test
	public void testUnsolidBlockAllowed() throws Exception {

		Sha256Hash sha256Hash1 = getRandomSha256Hash();
		Sha256Hash sha256Hash2 = getRandomSha256Hash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash1);
		block.setPrevBranchBlockHash(sha256Hash2);
		block.solve();
		System.out.println(block.getHashAsString());

		// Send over kafka method to allow unsolids
		blockService.addConnected(block.bitcoinSerialize(), true);
	}

	@Test
	public void testUnsolidBlockDisallowed() throws Exception {

		Sha256Hash sha256Hash1 = getRandomSha256Hash();
		Sha256Hash sha256Hash2 = getRandomSha256Hash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash1);
		block.setPrevBranchBlockHash(sha256Hash2);
		block.solve();
		System.out.println(block.getHashAsString());

		// Send over API method to disallow unsolids
		try {
			blockService.saveBlock(block, store);
			fail();
		} catch (VerificationException e) {
			// Expected
		}

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));
	}

	@Test
	public void testUnsolidBlockReconnectBlock() throws Exception {

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		depBlock.addTransaction(wallet.feeTransaction(null));
		depBlock.solve();
		Block block = depBlock.createNextBlock(depBlock);
		block.addTransaction(wallet.feeTransaction(null));
		block.solve();
		blockService.addConnected(block.bitcoinSerialize(), true);

		// Should not be solid
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 0);

		// Add missing dependency
		blockService.saveBlock(depBlock, store);

		// After adding the missing dependency, should be solid

		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).solidifyWaiting(block, store);
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 2);
		assertTrue(store.getBlockWrap(depBlock.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testUnsolidMissingPredecessor1() throws Exception {

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		depBlock.addTransaction(wallet.feeTransaction(null));
		depBlock.solve();
		Block block = depBlock.createNextBlock(depBlock);
		block.addTransaction(wallet.feeTransaction(null));
		block.solve();
		blockService.addConnected(block.bitcoinSerialize(), true);
		// Should not be solid
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 0);

		// Add missing dependency
		blockService.saveBlock(depBlock, store);

		// After adding the missing dependency, should be solid
		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).solidifyWaiting(block, store);
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 2);
		assertTrue(store.getBlockWrap(depBlock.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testUnsolidMissingPredecessor2() throws Exception {

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		depBlock.addTransaction(wallet.feeTransaction(null));
		depBlock.solve();
		Block block = depBlock.createNextBlock(depBlock);
		block.addTransaction(wallet.feeTransaction(null));
		block.solve();
		blockService.addConnected(block.bitcoinSerialize(), true);

		// Should not be solid
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 0);

		// Add missing dependency
		blockService.saveBlock(depBlock, store);

		// After adding the missing dependency, should be solid

		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).solidifyWaiting(block, store);
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 2);
		assertTrue(store.getBlockWrap(depBlock.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testSameUTXOInput() throws Exception {

		// use the same input data for other transaction in a block double spent
		Transaction tx1 = createTestTransaction();
		Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block1.addTransaction(tx1);
		block1.addTransaction(wallet.feeTransaction(null));
		block1 = adjustSolve(block1);
		try {
			this.blockGraph.add(block1, false, store);
			fail();
		} catch (Exception e) {
			// TODO: handle exception
		}

	}

	@Test
	public void testUnsolidMissingUTXO() throws Exception {

		// Create block with UTXO
		Transaction tx1 = createTestTransaction();
		Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1);
		Block confBlock = makeRewardBlock();

		// Create block with dependency

		Transaction tx2 = createTestTransaction();
		Block block = createAndAddNextBlockWithTransaction(confBlock, confBlock, tx2);

		 resetStore();

		// Add block allowing unsolids
		blockService.addConnected(confBlock.bitcoinSerialize(), false);
		blockService.addConnected(block.bitcoinSerialize(), true);

		// Should not be solid
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 0);

		// Add missing dependency
		blockService.saveBlock(depBlock, store);
		blockService.saveBlock(confBlock, store);

		// After adding the missing dependency, should be solid
		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).solidifyWaiting(block, store);

		// TODO
		// assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid()
		// == 2);
		assertTrue(store.getBlockWrap(depBlock.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testUnsolidMissingReward() throws Exception {

		// Generate blocks until passing first reward interval and second reward
		// interval
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock = addFixedBlocks(3, networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate eligible mining reward block
		Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		blockGraph.updateChain();

		// Mining reward block should go through
		assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());

		// Generate eligible second mining reward block
		Block rewardBlock2 = rewardService.createReward(rewardBlock1.getHash() , store);
		blockGraph.updateChain();

		 resetStore();
		for (Block b : blocksAddedAll) {
			blockGraph.add(b, true, store);
		}
		// Add block allowing unsolids
		blockService.addConnected(rewardBlock2.bitcoinSerialize(), true);
		blockGraph.updateChain();
		// Should not be solid
		assertTrue(store.getBlockWrap(rewardBlock2.getHash()) == null);

		// Add missing dependency
		blockService.saveBlock(rewardBlock1, store);

		blockGraph.updateChain();
		// After adding the missing dependency, should be solid
		blockGraph.add(rewardBlock2, true, true, store);
		syncBlockService.connectingOrphans(store);
		blockGraph.updateChain();
		assertTrue(store.getBlockWrap(rewardBlock2.getHash()).getBlockEvaluation().getSolid() == 2);
		assertTrue(store.getBlockWrap(rewardBlock1.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testUnsolidMissingToken() throws Exception {

		// Generate an eligible issuance
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		TokenInfo tokenInfo = new TokenInfo();
		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		Block depBlock = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null, null, false);
		mcmcServiceUpdate();
		// Generate second eligible issuance
		TokenInfo tokenInfo2 = new TokenInfo();
		Token tokens2 = Token.buildSimpleTokenInfo(true, depBlock.getHash(), Utils.HEX.encode(pubKey), "Test", "Test",
				1, 1, coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

		Block block = saveTokenUnitTestWithTokenname(tokenInfo2, coinbase, outKey, null);

		 resetStore();

		// Add block allowing unsolids
		blockService.addConnected(block.bitcoinSerialize(), true);

		// Should not be solid
		assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 0);

		// Add missing dependency
		blockService.saveBlock(depBlock, store);

		// After adding the missing dependency, should be solid

		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).solidifyWaiting(block, store);

		// There are prev not there TODO
		// assertTrue(store.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid()
		// == 2);
		assertTrue(store.getBlockWrap(depBlock.getHash()).getBlockEvaluation().getSolid() == 2);
	}

	@Test
	public void testSolidityPredecessorConsensusInheritance() throws Exception {

		// Generate blocks until passing first reward interval and second reward
		// interval
		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock = addFixedBlocks(3, networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate eligible mining reward block
		Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);

		// The consensus number should now be equal to the previous number + 1
		assertEquals(rollingBlock.getLastMiningRewardBlock() + 1, rewardBlock1.getLastMiningRewardBlock());

		for (int i = 0; i < 1; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The difficulty should be equal to the previous difficulty
			assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true, store);
		}

		try {
			Block failingBlock = rollingBlock.createNextBlock(networkParameters.getGenesisBlock());
			failingBlock.setLastMiningRewardBlock(2);
			failingBlock.addTransaction(wallet.feeTransaction(null));
			failingBlock.solve();
			blockGraph.add(failingBlock, false, store);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}

		try {
			Block failingBlock = networkParameters.getGenesisBlock().createNextBlock(rollingBlock);
			failingBlock.setLastMiningRewardBlock(2);
			failingBlock.addTransaction(wallet.feeTransaction(null));
			failingBlock.solve();
			blockGraph.add(failingBlock, false, store);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}

		blockGraph.updateChain();
		try {
			Block failingBlock = rewardService.createMiningRewardBlock(rewardBlock1.getHash(), defaultBlockWrap(rollingBlock ),
					defaultBlockWrap(rollingBlock), store);
			blockGraph.updateChain();
			failingBlock.setLastMiningRewardBlock(123);

			failingBlock.solve();
			blockGraph.add(failingBlock, false, store);
			blockGraph.updateChain();
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}
	}

	@Test
	public void testSolidityPredecessorTimeInheritance() throws Exception {

		// Generate blocks until passing first reward interval and second reward
		// interval
		Block rollingBlock = networkParameters.getGenesisBlock();
		for (int i = 0; i < 3; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The time should not be moving backwards
			assertTrue(rollingBlock.getTimeSeconds() <= rollingBlockNew.getTimeSeconds());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true, store);
		}

		// The time is allowed to stay the same
		rollingBlock = rollingBlock.createNextBlock(rollingBlock);
		rollingBlock.setTime(rollingBlock.getTimeSeconds()); // 01/01/2000 @
																// 12:00am (UTC)
		rollingBlock.solve();
		blockGraph.add(rollingBlock, true, store);

		// The time is not allowed to move backwards
		try {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			rollingBlock.setTime(946684800); // 01/01/2000 @ 12:00am (UTC)
			rollingBlock.addTransaction(wallet.feeTransaction(null));
			rollingBlock.solve();
			blockGraph.add(rollingBlock, false, store);
			fail();
		} catch (TimeReversionException e) {
		}
	}

	@Test
	public void testSolidityCoinbaseDisallowed() throws Exception {

		final Block genesisBlock = networkParameters.getGenesisBlock();

		// For disallowed types: coinbases are not allowed
		for (Type type : Block.Type.values()) {
			if (!type.allowCoinbaseTransaction())
				try {
					// Build transaction
					Transaction tx = new Transaction(networkParameters);
					tx.addOutput(Coin.COIN.times(2), new ECKey().toAddress(networkParameters));

					// The input does not really need to be a valid signature,
					// as long
					// as it has the right general form and is slightly
					// different for
					// different tx
					TransactionInput input = new TransactionInput(networkParameters, tx, Script
							.createInputScript(genesisBlock.getHash().getBytes(), genesisBlock.getHash().getBytes()));
					tx.addInput(input);

					// Check it fails
					Block rollingBlock = genesisBlock.createNextBlock(genesisBlock);
					rollingBlock.setBlockType(type);
					rollingBlock.addTransaction(tx);
					rollingBlock.solve();
					blockGraph.add(rollingBlock, false, store);

					fail();
				} catch (CoinbaseDisallowedException | UnsolidException e) {
				}
		}
	}

	@Test
	public void testSolidityTXDoubleSpend() throws Exception {

		// Create block with UTXOs
		Transaction tx1 = createTestTransaction();
		Block spenderBlock1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1);
		mcmc();
		Block spenderBlock2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1);

		// Confirm 1
		makeRewardBlock(spenderBlock1);

		// 1 should be confirmed now
		UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(spenderBlock1.getHash()), store);
		UTXO utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(spenderBlock1.getHash()), store);
		assertTrue(utxo1.isConfirmed());
		assertTrue(utxo2.isConfirmed());
		assertFalse(utxo1.isSpent());
		assertFalse(utxo2.isSpent());

		// 2 should be unconfirmed
		utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(spenderBlock2.getHash()), store);
		utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(spenderBlock2.getHash()), store);
		assertFalse(utxo1.isConfirmed());
		assertFalse(utxo2.isConfirmed());
		assertFalse(utxo1.isSpent());
		assertFalse(utxo2.isSpent());

		// Further manipulations on prev UTXOs
		UTXO origUTXO = store.getTransactionOutput(networkParameters.getGenesisBlock().getHash(),
				networkParameters.getGenesisBlock().getTransactions().get(0).getHash(), 0L);
		assertTrue(origUTXO.isConfirmed());
		assertTrue(origUTXO.isSpent());
		assertEquals(
				store.getTransactionOutputSpender(origUTXO.getBlockHash(), origUTXO.getTxHash(), origUTXO.getIndex())
						.getBlockHash(),
				spenderBlock1.getHash());

		// Confirm 2
		try {
			Block rewardBlock2 = makeRewardBlock(spenderBlock2);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityTXInputScriptsCorrect() throws Exception {

		// Create block with UTXO
		Transaction tx1 = createTestTransaction();
		createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(),
				tx1);

		 resetStore();

		// Again but with incorrect input script
		try {
			tx1.getInput(0).setScriptSig(new Script(new byte[0]));
			Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx1);
			block1 = adjustSolve(block1);
			this.blockGraph.add(block1, false, store);
			fail();
		} catch (ScriptException e) {
		}
	}

	// TODO @Test
	public void testSolidityTXOutputSumCorrect() throws Exception {

		// Create block with UTXO
		{
			Transaction tx1 = createTestTransaction();
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx1);
		}

		resetStore();

		// Again but with less output coins
		{

			ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
					Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0));
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(new TransactionOutput(networkParameters, tx2,
					spendableOutput.getValue().subtract(amount).subtract(amount), testKey));
			TransactionInput input = tx2.addInput(outputs.get(0).getBlockHash(), spendableOutput);
			Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx2);
		}

	 resetStore();

		// Again but with more output coins
		try {

			ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
					Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0));
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(new TransactionOutput(networkParameters, tx2,
					spendableOutput.getValue().subtract(Coin.FEE_DEFAULT), testKey));
			TransactionInput input = tx2.addInput(outputs.get(0).getBlockHash(), spendableOutput);
			Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			// tx2.getOutput(0).getValue().setValue(tx2.getOutput(0).getValue().getValue().add(BigInteger.valueOf(1)));
			Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx2);

			block1 = adjustSolve(block1);
			this.blockGraph.add(block1, false, store);
			fail();
		} catch (InvalidTransactionException e) {
		}
	}

	@Test
	public void testSolidityTXOutputNonNegative() throws Exception {

		// Create block with negative outputs
		try {

			ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
					Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0));
			Coin amount = Coin.valueOf(-1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(
					new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), testKey));
			TransactionInput input = tx2.addInput(outputs.get(0).getBlockHash(), spendableOutput);
			Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx2);
			fail();
		} catch (NegativeValueOutput e) {
			// Expected
		}
	}

	@Test
	public void testSolidityNewGenesis() throws Exception {

		// Create genesis block
		try {
			Block b = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			b.setBlockType(Type.BLOCKTYPE_INITIAL);
			b.solve();
			blockGraph.add(b, false, store);
			fail();
		} catch (GenesisBlockDisallowedException e) {
		}
	}

	@Test
	public void testSoliditySigOps() throws Exception {

		// Create block with outputs
		try {

			ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
					Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0));
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(
					new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), testKey));
			TransactionInput input = tx2.addInput(outputs.get(0).getBlockHash(), spendableOutput);

			ScriptBuilder scriptBuilder = new ScriptBuilder();
			for (int i = 0; i < NetworkParameters.MAX_BLOCK_SIGOPS + 1; i++)
				scriptBuilder.op(0xac);

			Script inputScript = scriptBuilder.build();
			input.setScriptSig(inputScript);
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx2);
			fail();
		} catch (SigOpsException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWrongDifficulty() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 20; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with spending inputs
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		rewardBlock.setDifficultyTarget(rollingBlock.getDifficultyTarget() * 2);

		// Should not go through
		try {
			rewardBlock.solve();
			blockGraph.add(rewardBlock, false, store);
			fail();
		} catch (VerificationException e) {

		}

	}

	@Test
	public void testSolidityRewardTxWithTransfers1() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 1 + 1 + 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with spending inputs
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		Transaction tx = rewardBlock.getTransactions().get(0);

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		List<UTXO> outputs = getBalance(false, testKey);
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
		Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
		tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
		tx.addOutput(
				new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
		TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

		TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false, true, store);

			fail();
		} catch (TransactionOutputsDisallowedException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWithTransfers2() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 1 + 1 + 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with additional tx
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		Transaction tx = createTestTransaction();
		rewardBlock.addTransaction(tx);
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false, true, store);

			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWithMissingRewardInfo() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 1 + 1 + 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with malformed tx data
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		rewardBlock.getTransactions().get(0).setData(null);
		rewardBlock.solve();

		// Should not go through
		try {
			assertFalse(blockGraph.add(rewardBlock, false, true, store));
			fail();
		} catch (RuntimeException e) {

		}

	}

	@Test
	public void testSolidityRewardTxMalformedData1() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 1 + 1 + 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with malformed tx data
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		rewardBlock.getTransactions().get(0).setData(new byte[] { 2, 3, 4 });
		;
		rewardBlock.solve();

		// Should not go through
		try {
			assertFalse(blockGraph.add(rewardBlock, false, true, store));
			fail();
		} catch (RuntimeException e) {
		}
	}

	@Test
	public void testSolidityRewardTxMalformedData2() throws Exception {

		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < 1 + 1 + 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}

		// Generate mining reward block with malformed fields
		Block rewardBlock = rewardService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock ),
				defaultBlockWrap(rollingBlock), store);
		blockGraph.updateChain();
		Block testBlock1 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock2 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock3 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock4 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock5 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock6 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock7 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		RewardInfo rewardInfo1 = new RewardInfo().parse(testBlock1.getTransactions().get(0).getData());
		RewardInfo rewardInfo2 = new RewardInfo().parse(testBlock2.getTransactions().get(0).getData());
		RewardInfo rewardInfo3 = new RewardInfo().parse(testBlock3.getTransactions().get(0).getData());
		RewardInfo rewardInfo4 = new RewardInfo().parse(testBlock4.getTransactions().get(0).getData());
		RewardInfo rewardInfo5 = new RewardInfo().parse(testBlock5.getTransactions().get(0).getData());
		RewardInfo rewardInfo6 = new RewardInfo().parse(testBlock6.getTransactions().get(0).getData());
		RewardInfo rewardInfo7 = new RewardInfo().parse(testBlock7.getTransactions().get(0).getData());
		rewardInfo3.setPrevRewardHash(getRandomSha256Hash());
		rewardInfo4.setPrevRewardHash(rollingBlock.getHash());
		rewardInfo5.setPrevRewardHash(rollingBlock.getHash());
		testBlock1.getTransactions().get(0).setData(rewardInfo1.toByteArray());
		testBlock2.getTransactions().get(0).setData(rewardInfo2.toByteArray());
		testBlock3.getTransactions().get(0).setData(rewardInfo3.toByteArray());
		testBlock4.getTransactions().get(0).setData(rewardInfo4.toByteArray());
		testBlock5.getTransactions().get(0).setData(rewardInfo5.toByteArray());
		testBlock6.getTransactions().get(0).setData(rewardInfo6.toByteArray());
		testBlock7.getTransactions().get(0).setData(rewardInfo7.toByteArray());
		testBlock1.solve();
		testBlock2.solve();
		testBlock3.solve();
		testBlock4.solve();
		testBlock5.solve();
		testBlock6.solve();
		testBlock7.solve();

		blockGraph.add(testBlock3, true, true, store);
		try {
			blockGraph.add(testBlock4, false, true, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(testBlock5, false, true, store);
			fail();
		} catch (VerificationException e) {
		}
	}

	interface TestCase {
		public boolean expectsException();

		public void preApply(TokenInfo info);
	}

	@Test
	public void testSolidityTokenMalformedData1() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
		block.getTransactions().get(0).setData(null);

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);
			fail();
		} catch (MissingTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedData2() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
		block.getTransactions().get(0).setData(new byte[] { 1, 2 });

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);
			fail();
		} catch (MalformedTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedDataSignature1() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokens.setDomainName("bc");

		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
		block.getTransactions().get(0).setDataSignature(null);

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);
			fail();
		} catch (MissingTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedDataSignature2() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokens.setDomainName("bc");

		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
		block.getTransactions().get(0).setDataSignature(new byte[] { 1, 2 });

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);
			fail();
		} catch (MalformedTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMutatedData() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo0 = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokens.setDomainName("bc");

		tokenInfo0.setToken(tokens);
		tokenInfo0.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		tokens.setDomainName("bc");

		TestCase[] executors = new TestCase[] {
				// 1
				new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.setToken(null);
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 2
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.setMultiSignAddresses(null);
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 3
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.getToken().setAmount(new BigInteger("-1"));
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 4
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setBlockHash(null);
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 5
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setDescription(null);
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 6
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken()
								.setDescription(new String(new char[Token.TOKEN_MAX_DESC_LENGTH]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 7
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setDescription(
								new String(new char[Token.TOKEN_MAX_DESC_LENGTH + 1]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 8
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenstop(false);
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 9
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.getToken().setPrevblockhash(null);
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 10
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setPrevblockhash(getRandomSha256Hash());
						tokenInfo5.getToken().setTokenindex(1);
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 11
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.getToken().setTokenindex(1);
						tokenInfo5.getToken().setPrevblockhash(networkParameters.getGenesisBlock().getHash());
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 12
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setSignnumber(-1);
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() { // 13
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setSignnumber(0);
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 14
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenid(null);
					}

					@Override
					public boolean expectsException() {
						return true;
					}
				}, new TestCase() {
					// 15
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenid("");
					}

					@Override
					public boolean expectsException() {
						return false;
					}
				}, new TestCase() {
					// 16
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenid("test");
					}

					@Override
					public boolean expectsException() {
						return true;// TODO add check
					}
				}, new TestCase() {
					// 17
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenid(Utils.HEX.encode(testKey.getPubKey()));
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					// 18
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenindex(-1);
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					// 19
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenindex(5);
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					// 21
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenname(null);
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					// 22
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenname("");
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					// 23
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken()
								.setTokenname(new String(new char[Token.TOKEN_MAX_NAME_LENGTH]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					// 24
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken()
								.setTokenname(new String(new char[Token.TOKEN_MAX_NAME_LENGTH + 1]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() { // 25
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokenstop(false);
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					// 26
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setTokentype(-1);
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {
						tokenInfo5.getToken().setDomainName(null);
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					// 28
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken().setDomainName("");
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getToken()
								.setDomainName(new String(new char[Token.TOKEN_MAX_URL_LENGTH]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) { // 30

						tokenInfo5.getToken()
								.setDomainName(new String(new char[Token.TOKEN_MAX_URL_LENGTH + 1]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().remove(0);
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setAddress(null);
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setAddress("");
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0)
								.setAddress(new String(new char[222]).replace("\0", "A"));
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {// 35

						tokenInfo5.getMultiSignAddresses().get(0).setBlockhash(null);
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setBlockhash(getRandomSha256Hash());
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0)
								.setBlockhash(networkParameters.getGenesisBlock().getHash());
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(-1);
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) { // 40

						tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(0);
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(4);
					}

					@Override
					public boolean expectsException() {
						return false; // these do not matter, they are
										// overwritten

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0)
								.setPubKeyHex(Utils.HEX.encode(new ECKey().getPubKey()));
					}

					@Override
					public boolean expectsException() {
						return true;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setTokenid(null);
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setTokenid("");
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) { // 45
						tokenInfo5.getMultiSignAddresses().get(0).setTokenid("test");
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				}, new TestCase() {
					@Override
					public void preApply(TokenInfo tokenInfo5) {

						tokenInfo5.getMultiSignAddresses().get(0).setTokenid(Utils.HEX.encode(testKey.getPubKey()));
					}

					@Override
					public boolean expectsException() {
						return false;

					}
				} };

		for (int i = 0; i < executors.length; i++) {
			// Modify the tokenInfo
			TokenInfo tokenInfo = new TokenInfo().parse(tokenInfo0.toByteArray());
			executors[i].preApply(tokenInfo);

			// Make block including it
			Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

			// Coinbase with signatures
			if (tokenInfo.getMultiSignAddresses() != null) {

				block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
				Transaction transaction = block.getTransactions().get(0);
				Sha256Hash sighash1 = transaction.getHash();
				ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
				byte[] buf1 = party1Signature.encodeToDER();

				List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
				MultiSignBy multiSignBy0 = new MultiSignBy();
				if (tokenInfo.getToken() != null && tokenInfo.getToken().getTokenid() != null)
					multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
				else
					multiSignBy0.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
				multiSignBy0.setTokenindex(0);
				multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
				multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
				multiSignBy0.setSignature(Utils.HEX.encode(buf1));
				multiSignBies.add(multiSignBy0);

				ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
						Utils.HEX.decode(testPub));
				ECKey.ECDSASignature party2Signature = genesiskey.sign(sighash1, aesKey);
				byte[] buf2 = party2Signature.encodeToDER();
				multiSignBy0 = new MultiSignBy();
				if (tokenInfo.getToken() != null && tokenInfo.getToken().getTokenid() != null)
					multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
				else
					multiSignBy0.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
				multiSignBy0.setTokenindex(0);
				multiSignBy0.setAddress(genesiskey.toAddress(networkParameters).toBase58());
				multiSignBy0.setPublickey(Utils.HEX.encode(genesiskey.getPubKey()));
				multiSignBy0.setSignature(Utils.HEX.encode(buf2));
				multiSignBies.add(multiSignBy0);

				MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
				transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
			}

			// solve block
			block.solve();

			// Should not go through
			if (executors[i].expectsException()) {
				try {
					blockGraph.add(block, false, store);
					fail("Number " + i + " failed");
				} catch (VerificationException e) {
				}
			} else {
				// always add
				blockGraph.add(block, true, store);
			}
		}
	}

	@Test
	public void testSolidityTokenMutatedDataSignatures() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		ECKey outKey2 = new ECKey();
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokens.setDomainName("bc");
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, null);
		Transaction transaction = block.getTransactions().get(0);

		Sha256Hash sighash1 = transaction.getHash();
		ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
		byte[] buf1 = party1Signature.encodeToDER();

		List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
		MultiSignBy multiSignBy0 = new MultiSignBy();
		multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
		multiSignBy0.setTokenindex(0);
		multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
		multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
		multiSignBy0.setSignature(Utils.HEX.encode(buf1));
		multiSignBies.add(multiSignBy0);

		ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
				Utils.HEX.decode(testPub));
		ECKey.ECDSASignature party2Signature = genesiskey.sign(sighash1, aesKey);
		byte[] buf2 = party2Signature.encodeToDER();
		multiSignBy0 = new MultiSignBy();
		if (tokenInfo.getToken() != null && tokenInfo.getToken().getTokenid() != null)
			multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
		else
			multiSignBy0.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
		multiSignBy0.setTokenindex(0);
		multiSignBy0.setAddress(genesiskey.toAddress(networkParameters).toBase58());
		multiSignBy0.setPublickey(Utils.HEX.encode(genesiskey.getPubKey()));
		multiSignBy0.setSignature(Utils.HEX.encode(buf2));
		multiSignBies.add(multiSignBy0);

		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
		block.addTransaction(wallet.feeTransaction(null));
		// Mutate signatures
		Block block1 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block2 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block3 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block4 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block5 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block6 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block7 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block8 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block9 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block10 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block11 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block12 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block13 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block14 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block15 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block16 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block17 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
		Block block18 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());

		MultiSignByRequest multiSignByRequest1 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest2 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest3 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest4 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest5 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest6 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest7 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest8 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest9 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest10 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest11 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest12 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest13 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest14 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest15 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest16 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest17 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);
		MultiSignByRequest multiSignByRequest18 = Json.jsonmapper().readValue(transaction.getDataSignature(),
				MultiSignByRequest.class);

		multiSignByRequest1.setMultiSignBies(null);
		multiSignByRequest2.setMultiSignBies(new ArrayList<>());
		multiSignByRequest3.getMultiSignBies().get(0).setAddress(null);
		multiSignByRequest4.getMultiSignBies().get(0).setAddress("");
		multiSignByRequest5.getMultiSignBies().get(0).setAddress("test");
		multiSignByRequest6.getMultiSignBies().get(0).setPublickey(null);
		multiSignByRequest7.getMultiSignBies().get(0).setPublickey("");
		multiSignByRequest8.getMultiSignBies().get(0).setPublickey("test");
		multiSignByRequest9.getMultiSignBies().get(0).setPublickey(Utils.HEX.encode(outKey2.getPubKey()));
		multiSignByRequest10.getMultiSignBies().get(0).setSignature(null);
		multiSignByRequest11.getMultiSignBies().get(0).setSignature("");
		multiSignByRequest12.getMultiSignBies().get(0).setSignature("test");
		multiSignByRequest13.getMultiSignBies().get(0).setSignature(Utils.HEX.encode(outKey2.getPubKey()));
		multiSignByRequest14.getMultiSignBies().get(0).setTokenid(null);
		multiSignByRequest15.getMultiSignBies().get(0).setTokenid("");
		multiSignByRequest16.getMultiSignBies().get(0).setTokenid("test");
		multiSignByRequest17.getMultiSignBies().get(0).setTokenindex(-1);
		multiSignByRequest18.getMultiSignBies().get(0).setTokenindex(1);

		block1.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest1));
		block2.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest2));
		block3.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest3));
		block4.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest4));
		block5.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest5));
		block6.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest6));
		block7.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest7));
		block8.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest8));
		block9.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest9));
		block10.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest10));
		block11.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest11));
		block12.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest12));
		block13.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest13));
		block14.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest14));
		block15.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest15));
		block16.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest16));
		block17.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest17));
		block18.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest18));

		block1.solve();
		block2.solve();
		block3.solve();
		block4.solve();
		block5.solve();
		block6.solve();
		block7.solve();
		block8.solve();
		block9.solve();
		block10.solve();
		block11.solve();
		block12.solve();
		block13.solve();
		block14.solve();
		block15.solve();
		block16.solve();
		block17.solve();
		block18.solve();

		// Test
		try {
			blockGraph.add(block1, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block2, false, store);
			fail();
		} catch (VerificationException e) {
		}

		try {
			blockGraph.add(block3, false, store);
			// fail();
		} catch (VerificationException e) {

		}
		try {
			blockGraph.add(block4, false, store);
			// TODO fail();
		} catch (VerificationException e) {

		}
		try {
			blockGraph.add(block5, false, store);
			// TODO fail();
		} catch (VerificationException e) {

		}
		try {
			blockGraph.add(block6, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block7, false, store);
			// TODO fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block8, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block9, false, store);
			// TODO fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block10, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block11, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block12, false, store);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block13, false, store);
			fail();
		} catch (VerificationException e) {
		}

		try {
			blockGraph.add(block14, false, store);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block15, false, store);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block16, false, store);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block17, false, store);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block18, false, store);
		} catch (VerificationException e) {
			fail();
		}
	}

	@Test
	public void testSolidityTokenNoTransaction() throws Exception {

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityTokenTransferTransaction() throws Exception {

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Add transfer transaction
		Transaction tx = createTestTransaction();
		block.addTransaction(tx);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);

			fail();
		} catch (NotCoinbaseException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorWrongTokenid() throws JsonProcessingException, Exception {

		// Generate an eligible issuance
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

		// Generate a subsequent issuance that does not work
		byte[] pubKey2 = new ECKey().getPubKey();
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey2);

		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey2), "Test", "Test",
				1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", new ECKey().getPublicKeyAsHex()));
		try {

			Block block = makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
			blockGraph.add(block, false, store);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityTokenWrongTokenindex() throws JsonProcessingException, Exception {

		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);

		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test", "Test", 1,
				2, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {

			Block block = makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
			blockGraph.add(block, false, store);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorStopped() throws JsonProcessingException, Exception {

		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);

		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test", "Test", 1,
				1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {

			Block block = makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
			blockGraph.add(block, false, store);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorConflictingType() throws JsonProcessingException, Exception {

		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);

		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test", "Test", 1,
				1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokens2.setTokentype(123);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {

			Block block = makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
			blockGraph.add(block, false, store);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorConflictingName() throws JsonProcessingException, Exception {

		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);

		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test2", "Test",
				1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {

			Block block = makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
			blockGraph.add(block, false, store);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenWrongTokenCoinbase() throws Exception {

		// Generate an eligible issuance tokenInfo
		ECKey outKey = wallet.walletKeys().get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
				coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo, new MemoInfo("coinbase"));
		Transaction transaction = block.getTransactions().get(0);

		// Add another output for other tokens
		block.getTransactions().get(0).addOutput(Coin.COIN.times(2), outKey.toAddress(networkParameters));

		Sha256Hash sighash1 = transaction.getHash();
		ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
		byte[] buf1 = party1Signature.encodeToDER();

		List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
		MultiSignBy multiSignBy0 = new MultiSignBy();
		multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
		multiSignBy0.setTokenindex(0);
		multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
		multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
		multiSignBy0.setSignature(Utils.HEX.encode(buf1));
		multiSignBies.add(multiSignBy0);
		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false, store);

			fail();
		} catch (InvalidTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenNoTransactions() throws Exception {

		Block block1 = null;
		{
			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false, store);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenMultipleTXs() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		Block block1;

		TokenInfo tokenInfo = new TokenInfo();

		Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());

		Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(testKey.getPubKey()), "Test", "Test", 1,
				0, coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

		// This (saveBlock) calls milestoneUpdate currently
		tokenBlock = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
		makeRewardBlock();

		// Make a buy order for "test"s
		Transaction tx = new Transaction(networkParameters);
		OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
				testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, 3,
				NetworkParameters.BIGTANGLE_TOKENID_STRING);
		tx.setData(info.toByteArray());
		tx.setDataClassName("OrderOpen");

		// Create burning 2 BIG
		List<UTXO> outputs = getBalance(false, testKey).stream().filter(out -> Utils.HEX
				.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
				.collect(Collectors.toList());
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
		Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
		// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
		// amount, testKey));
		tx.addOutput(
				new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
		TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
		TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);

		Transaction tx2 = new Transaction(networkParameters);
		// Create burning 2 "test"
		List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
				out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
				.collect(Collectors.toList());
		TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters, outputs2.get(0));
		Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
		// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
		// amount2, testKey));
		tx2.addOutput(
				new TransactionOutput(networkParameters, tx2, spendableOutput2.getValue().subtract(amount2), testKey));
		TransactionInput input2 = tx2.addInput(outputs2.get(0).getBlockHash(), spendableOutput2);
		Sha256Hash sighash2 = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
		TransactionSignature sig2 = new TransactionSignature(testKey.sign(sighash2), Transaction.SigHash.ALL, false);
		Script inputScript2 = ScriptBuilder.createInputScript(sig2);
		input2.setScriptSig(inputScript2);

		// Create block with order
		block1 = tokenBlock.createNextBlock(tokenBlock);
		block1.addTransaction(tx);
		block1.addTransaction(tx2);
		block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
		block1.solve();

		// Should not go through
		try {
			blockGraph.add(block1, false, store);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenNoTokensOffered() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, 3,
					NetworkParameters.BIGTANGLE_TOKENID_STRING);
			tx.setData(info.toByteArray());
			tx.setDataClassName("OrderOpen");

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			block1.addTransaction(wallet.feeTransaction(null));
			block1.solve();
			blockGraph.add(block1, false, store);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenMultipleTokens() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());

			Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
			makeRewardBlock();
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, 3,
					NetworkParameters.BIGTANGLE_TOKENID_STRING);
			tx.setData(info.toByteArray());
			tx.setDataClassName("OrderOpen");

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey).stream()
					.filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
							.equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
					.collect(Collectors.toList());
			UTXO output = getLargeUTXO(outputs);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, output);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx,
					spendableOutput.getValue().subtract(amount).subtract(Coin.FEE_DEFAULT), testKey));

			// Create burning 2 "test"
			List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters,
					outputs2.get(0));
			Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount2, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput2.getValue().subtract(amount2),
					testKey));

			TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
			TransactionInput input2 = tx.addInput(outputs2.get(0).getBlockHash(), spendableOutput2);

			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			Sha256Hash sighash2 = tx.hashForSignature(1, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig2 = new TransactionSignature(testKey.sign(sighash2), Transaction.SigHash.ALL,
					false);
			Script inputScript2 = ScriptBuilder.createInputScript(sig2);
			input2.setScriptSig(inputScript2);

			// Create block with order
			block1 = tokenBlock.createNextBlock(tokenBlock);
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			// block1.addTransaction(wallet.feeTransaction(null));
			block1.solve();
			blockGraph.add(block1, false, store);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenNoBIGs() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinTestkey = Coin.valueOf(77777L, testKey.getPubKey());

			Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, coinTestkey.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = saveTokenUnitTest(tokenInfo, coinTestkey, testKey, null);
			makeRewardBlock();
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test2", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, 3,
					NetworkParameters.BIGTANGLE_TOKENID_STRING);
			tx.setData(info.toByteArray());
			tx.setDataClassName("OrderOpen");

			// Create burning 2 "test"
			List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters,
					outputs2.get(0));
			Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount2, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput2.getValue().subtract(amount2),
					testKey));

			TransactionInput input2 = tx.addInput(outputs2.get(0).getBlockHash(), spendableOutput2);

			Sha256Hash sighash2 = tx.hashForSignature(0, spendableOutput2.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig2 = new TransactionSignature(testKey.sign(sighash2), Transaction.SigHash.ALL,
					false);
			Script inputScript2 = ScriptBuilder.createInputScript(sig2);
			input2.setScriptSig(inputScript2);

			// Create block with order
			block1 = tokenBlock.createNextBlock(tokenBlock);
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			block1.addTransaction(wallet.feeTransaction(null));
			block1.solve();
			blockGraph.add(block1, false, store);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpOk() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey tokenKey = new ECKey();
		Block pre = makeTestToken(tokenKey, new ArrayList<Block>());

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, tokenKey.getPublicKeyAsHex(), testKey.getPubKey(), null, null,
					Side.BUY, testKey.toAddress(networkParameters).toBase58(),
					NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, amount.getValue().longValue(),
					NetworkParameters.BIGTANGLE_TOKENID_STRING);
			tx.setData(info.toByteArray());
			tx.setDataClassName("OrderOpen");

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0));

			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx,
					spendableOutput.getValue().subtract(amount).subtract(Coin.FEE_DEFAULT), testKey));
			TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = pre.createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);

			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();

			// Should go through
			blockGraph.add(block1, false, store);
			mcmc();
		}
		Block block2 = null;
		{
			Transaction fee = wallet.feeTransaction(null);
			// Make an order op
			Transaction tx = new Transaction(networkParameters);
			OrderCancelInfo info = new OrderCancelInfo(block1.getHash());
			tx.setData(info.toByteArray());

			// Legitimate it by signing
			Sha256Hash sighash1 = tx.getHash();
			ECKey.ECDSASignature party1Signature = testKey.sign(sighash1, null);
			byte[] buf1 = party1Signature.encodeToDER();
			tx.setDataSignature(buf1);

			// Create block with order
			block2 = block1.createNextBlock(block1);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_CANCEL);
			block2.addTransaction(fee);
			block2.solve();
		}

		// Should go through
		blockGraph.add(block2, false, store);
	}

	@Test
	public void testSolidityOrderOpWrongSig() throws Exception {

		ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey tokenKey = new ECKey();
		Block pre = makeTestToken(tokenKey, new ArrayList<Block>());

		Block block2 = null;
		{
			// Make an order op
			Transaction tx = new Transaction(networkParameters);
			OrderCancelInfo info = new OrderCancelInfo(pre.getHash());
			tx.setData(info.toByteArray());

			// Legitimate it by signing
			Sha256Hash sighash1 = tx.getHash();
			ECKey.ECDSASignature party1Signature = testKey.sign(sighash1, null);
			byte[] buf1 = party1Signature.encodeToDER();
			buf1[0] = 0;
			buf1[1] = 0;
			buf1[2] = 0;
			tx.setDataSignature(buf1);

			// Create block with order
			block2 = pre.createNextBlock(pre);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_CANCEL);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false, store);
			fail();
		} catch (VerificationException e) {
		}
	}

}