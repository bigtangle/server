/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpInfo;
import net.bigtangle.core.OrderOpInfo.OrderOp;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
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
import net.bigtangle.core.exception.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionException;
import net.bigtangle.core.exception.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.exception.VerificationException.MissingDependencyException;
import net.bigtangle.core.exception.VerificationException.MissingSignatureException;
import net.bigtangle.core.exception.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.exception.VerificationException.NegativeValueOutput;
import net.bigtangle.core.exception.VerificationException.NotCoinbaseException;
import net.bigtangle.core.exception.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.exception.VerificationException.ProofOfWorkException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeReversionException;
import net.bigtangle.core.exception.VerificationException.TimeTravelerException;
import net.bigtangle.core.exception.VerificationException.TransactionOutputsDisallowedException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ValidatorServiceTest extends AbstractIntegrationTest {

	@Test
	public void testVerificationFutureTimestamp() throws Exception {
		store.resetStore();

		Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
		Block r1 = blockService.getBlock(tipsToApprove.getLeft());
		Block r2 = blockService.getBlock(tipsToApprove.getRight());
		Block b = r2.createNextBlock(r1);
		b.setTime(1577836800); // 01/01/2020 @ 12:00am (UTC)
		b.solve();
		try {
			blockService.saveBlock(b);
			fail();
		} catch (TimeTravelerException e) {
		}
	}

	@Test
	public void testVerificationIncorrectPoW() throws Exception {
		store.resetStore();

		Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
		Block r1 = blockService.getBlock(tipsToApprove.getLeft());
		Block r2 = blockService.getBlock(tipsToApprove.getRight());
		Block b = r2.createNextBlock(r1);
		b.setNonce(1377836800);
		try {
			blockService.saveBlock(b);
			fail();
		} catch (ProofOfWorkException e) {
		}
	}

	@Test
	public void testUnsolidBlockAllowed() throws Exception {
		store.resetStore();

		Sha256Hash sha256Hash1 = getRandomSha256Hash();
		Sha256Hash sha256Hash2 = getRandomSha256Hash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash1);
		block.setPrevBranchBlockHash(sha256Hash2);
		block.solve();
		System.out.println(block.getHashAsString());

		// Send over kafka method to allow unsolids
		transactionService.addConnected(block.bitcoinSerialize(), true, false);
	}

	@Test
	public void testUnsolidBlockDisallowed() throws Exception {
		store.resetStore();

		Sha256Hash sha256Hash1 = getRandomSha256Hash();
		Sha256Hash sha256Hash2 = getRandomSha256Hash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash1);
		block.setPrevBranchBlockHash(sha256Hash2);
		block.solve();
		System.out.println(block.getHashAsString());

		// Send over API method to disallow unsolids
		blockService.saveBlock(block);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));
	}

	@Test
	public void testUnsolidBlockReconnectBlock() throws Exception {
		store.resetStore();

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());

		Sha256Hash sha256Hash = depBlock.getHash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash);
		block.setPrevBranchBlockHash(sha256Hash);
		block.solve();
		System.out.println(block.getHashAsString());
		transactionService.addConnected(block.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));

		// Add missing dependency
		blockService.saveBlock(depBlock);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block.getHash()));
		assertNotNull(store.get(depBlock.getHash()));
	}

	@Test
	public void testUnsolidMissingPredecessor1() throws Exception {
		store.resetStore();

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());

		Sha256Hash sha256Hash = depBlock.getHash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(sha256Hash);
		block.setPrevBranchBlockHash(networkParameters.getGenesisBlock().getHash());
		block.solve();
		System.out.println(block.getHashAsString());
		transactionService.addConnected(block.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));

		// Add missing dependency
		blockService.saveBlock(depBlock);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block.getHash()));
		assertNotNull(store.get(depBlock.getHash()));
	}

	@Test
	public void testUnsolidMissingPredecessor2() throws Exception {
		store.resetStore();

		Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());

		Sha256Hash sha256Hash = depBlock.getHash();
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setPrevBlockHash(networkParameters.getGenesisBlock().getHash());
		block.setPrevBranchBlockHash(sha256Hash);
		block.solve();
		System.out.println(block.getHashAsString());
		transactionService.addConnected(block.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));

		// Add missing dependency
		blockService.saveBlock(depBlock);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block.getHash()));
		assertNotNull(store.get(depBlock.getHash()));
	}

	@Test
	public void testUnsolidMissingUTXO() throws Exception {
		store.resetStore();

		// Create block with UTXO
		Transaction tx1 = createTestGenesisTransaction();
		Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx1);

		blockGraph.confirm(depBlock.getHash(), new HashSet<>());

		// Create block with dependency
		Transaction tx2 = createTestGenesisTransaction();
		Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
				networkParameters.getGenesisBlock(), tx2);

		store.resetStore();

		// Add block allowing unsolids
		transactionService.addConnected(block.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));

		// Add missing dependency
		blockService.saveBlock(depBlock);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block.getHash()));
		assertNotNull(store.get(depBlock.getHash()));
	}

	@Test
	public void testUnsolidMissingReward() throws Exception {
		store.resetStore();
		List<Block> blocks1 = new ArrayList<>();
		List<Block> blocks2 = new ArrayList<>();

		// Generate blocks until passing first reward interval and second reward
		// interval
		Block rollingBlock = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blocks1.add(rollingBlock);
		}
		for (Block b : blocks1) {
			blockGraph.add(b, true);
			blockGraph.confirm(b.getHash(), new HashSet<Sha256Hash>());
		}

		// Generate eligible mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());
		blockGraph.confirm(rewardBlock1.getHash(), new HashSet<Sha256Hash>());

		// Mining reward block should go through
		assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());

		// Make more for next reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blocks2.add(rollingBlock);
		}
		for (Block b : blocks2) {
			blockGraph.add(b, true);
			blockGraph.confirm(b.getHash(), new HashSet<Sha256Hash>());
		}

		// Generate eligible second mining reward block
		Block rewardBlock2 = transactionService.createAndAddMiningRewardBlock(rewardBlock1.getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		blockGraph.confirm(rewardBlock2.getHash(), new HashSet<Sha256Hash>());

		store.resetStore();
		for (Block b : blocks1) {
			blockGraph.add(b, true);
			blockGraph.confirm(b.getHash(), new HashSet<Sha256Hash>());
		}
		for (Block b : blocks2) {
			blockGraph.add(b, true);
			blockGraph.confirm(b.getHash(), new HashSet<Sha256Hash>());
		}

		// Add block allowing unsolids
		transactionService.addConnected(rewardBlock2.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(rewardBlock2.getHash()));

		// Add missing dependency
		blockService.saveBlock(rewardBlock1);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(rewardBlock1.getHash()));
		assertNotNull(store.get(rewardBlock2.getHash()));
	}

	@Test
	public void testUnsolidMissingToken() throws Exception {
		store.resetStore();

		// Generate an eligible issuance
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();

		TokenInfo tokenInfo = new TokenInfo();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				false);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		Block depBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate second eligible issuance
		TokenInfo tokenInfo2 = new TokenInfo();
		Token tokens2 = Token.buildSimpleTokenInfo(true, depBlock.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
				"Test", 1, 1, amount, false);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

		Block block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase, outKey, null,
				networkParameters.getGenesisBlock().getHash(), networkParameters.getGenesisBlock().getHash());

		store.resetStore();

		// Add block allowing unsolids
		transactionService.addConnected(block.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block.getHash()));

		// Add missing dependency
		blockService.saveBlock(depBlock);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block.getHash()));
		assertNotNull(store.get(depBlock.getHash()));
	}

	@Test
	public void testUnsolidMissingOrderReclaimOrderMatching() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		List<Block> premiseBlocks = new ArrayList<>();

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			premiseBlocks.add(block1);
			this.blockGraph.add(block1, true);
		}

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			premiseBlocks.add(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rewardBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
			this.blockGraph.add(block2, false);
		}

		// Now reset and readd all but dependency and unsolid block
		store.resetStore();
		for (Block b : premiseBlocks) {
			blockGraph.add(b, false);
		}

		// Add block allowing unsolids
		transactionService.addConnected(block2.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block2.getHash()));

		// Add missing dependency
		blockService.saveBlock(rewardBlock1);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block2.getHash()));
		assertNotNull(store.get(rewardBlock1.getHash()));
	}

	@Test
	public void testUnsolidMissingOrderReclaimOrder() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		List<Block> premiseBlocks = new ArrayList<>();

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			this.blockGraph.add(block1, true);
		}

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			premiseBlocks.add(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());
		premiseBlocks.add(rewardBlock1);
		Block fusingBlock = rewardBlock1.createNextBlock(block1);
		premiseBlocks.add(fusingBlock);
		blockGraph.add(fusingBlock, false);

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rewardBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
			this.blockGraph.add(block2, false);
		}

		// Now reset and readd all but dependency and unsolid block
		store.resetStore();
		for (Block b : premiseBlocks) {
			blockGraph.add(b, false);
		}

		// Add block allowing unsolids
		transactionService.addConnected(block2.bitcoinSerialize(), true, false);

		// Should not be added since insolid
		assertNull(store.get(block2.getHash()));

		// Add missing dependency
		blockService.saveBlock(block1);

		// After adding the missing dependency, should be added
		assertNotNull(store.get(block2.getHash()));
		assertNotNull(store.get(block1.getHash()));
	}

	@Test
	public void testSolidityPredecessorDifficultyInheritance() throws Exception {
		store.resetStore();

		// Generate blocks until passing first reward interval and second reward
		// interval
		Block rollingBlock = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The difficulty should be equal to the previous difficulty
			assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true);
			blockGraph.confirm(rollingBlock.getHash(), new HashSet<Sha256Hash>());
		}

		// Generate eligible mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

		// The difficulty should now not be equal to the previous difficulty
		assertNotEquals(rollingBlock.getDifficultyTarget(), rewardBlock1.getDifficultyTarget());

		rollingBlock = rewardBlock1;
		for (int i = 0; i < 3; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The difficulty should be equal to the previous difficulty
			assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true);
		}

		try {
			Block failingBlock = rollingBlock.createNextBlock(networkParameters.getGenesisBlock());
			failingBlock.setDifficultyTarget(networkParameters.getGenesisBlock().getDifficultyTarget());
			failingBlock.solve();
			blockGraph.add(failingBlock, false);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}

		try {
			Block failingBlock = networkParameters.getGenesisBlock().createNextBlock(rollingBlock);
			failingBlock.setDifficultyTarget(networkParameters.getGenesisBlock().getDifficultyTarget());
			failingBlock.solve();
			blockGraph.add(failingBlock, false);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}
	}

	@Test
	public void testSolidityPredecessorConsensusInheritance() throws Exception {
		store.resetStore();

		// Generate blocks until passing first reward interval and second reward
		// interval
		Block rollingBlock = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The difficulty should be equal to the previous difficulty
			assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true);
			blockGraph.confirm(rollingBlock.getHash(), new HashSet<Sha256Hash>());
		}

		// Generate eligible mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

		// The consensus number should now be equal to the previous number + 1
		assertEquals(rollingBlock.getLastMiningRewardBlock() + 1, rewardBlock1.getLastMiningRewardBlock());

		for (int i = 0; i < 3; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The difficulty should be equal to the previous difficulty
			assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true);
		}

		try {
			Block failingBlock = rollingBlock.createNextBlock(networkParameters.getGenesisBlock());
			failingBlock.setLastMiningRewardBlock(2);
			failingBlock.solve();
			blockGraph.add(failingBlock, false);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}

		try {
			Block failingBlock = networkParameters.getGenesisBlock().createNextBlock(rollingBlock);
			failingBlock.setLastMiningRewardBlock(2);
			failingBlock.solve();
			blockGraph.add(failingBlock, false);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}

		try {
			Block failingBlock = transactionService.createMiningRewardBlock(rewardBlock1.getHash(),
					rollingBlock.getHash(), rollingBlock.getHash(), true);
			failingBlock.setLastMiningRewardBlock(123);
			failingBlock.solve();
			blockGraph.add(failingBlock, false);
			fail();
		} catch (DifficultyConsensusInheritanceException e) {
			// Expected
		}
	}

	@Test
	public void testSolidityPredecessorTimeInheritance() throws Exception {
		store.resetStore();

		// Generate blocks until passing first reward interval and second reward
		// interval
		Block rollingBlock = networkParameters.getGenesisBlock();
		for (int i = 0; i < 3; i++) {
			Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);

			// The time should not be moving backwards
			assertTrue(rollingBlock.getTimeSeconds() <= rollingBlockNew.getTimeSeconds());

			rollingBlock = rollingBlockNew;
			blockGraph.add(rollingBlock, true);
		}

		// The time is allowed to stay the same
		rollingBlock = rollingBlock.createNextBlock(rollingBlock);
		rollingBlock.setTime(rollingBlock.getTimeSeconds()); // 01/01/2000 @
																// 12:00am (UTC)
		rollingBlock.solve();
		blockGraph.add(rollingBlock, true);

		// The time is not allowed to move backwards
		try {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			rollingBlock.setTime(946684800); // 01/01/2000 @ 12:00am (UTC)
			rollingBlock.solve();
			blockGraph.add(rollingBlock, true);
			fail();
		} catch (TimeReversionException e) {
		}
	}

	@Test
	public void testSolidityCoinbaseDisallowed() throws Exception {
		store.resetStore();
		final Block genesisBlock = networkParameters.getGenesisBlock();

		// For disallowed types: coinbases are not allowed
		for (Type type : Block.Type.values()) {
			if (!type.allowCoinbaseTransaction())
				try {
					// Build transaction
					Transaction tx = new Transaction(networkParameters);
					tx.addOutput(Coin.SATOSHI.times(2), walletKeys.get(8).toAddress(networkParameters));

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
					blockGraph.add(rollingBlock, false);

					fail();
				} catch (CoinbaseDisallowedException e) {
				}
		}
	}

	@Test
	public void testSolidityTXInputScriptsCorrect() throws Exception {
		store.resetStore();

		// Create block with UTXO
		Transaction tx1 = createTestGenesisTransaction();
		createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(),
				tx1);

		store.resetStore();

		// Again but with incorrect input script
		try {
			tx1.getInput(0).setScriptSig(new Script(new byte[0]));
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx1);
			fail();
		} catch (ScriptException e) {
		}
	}

	@Test
	public void testSolidityTXOutputSumCorrect() throws Exception {
		store.resetStore();

		// Create block with UTXO
		{
			Transaction tx1 = createTestGenesisTransaction();
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx1);
		}

		store.resetStore();

		// Again but with less output coins
		{
			@SuppressWarnings("deprecation")
			ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(new TransactionOutput(networkParameters, tx2,
					spendableOutput.getValue().subtract(amount).subtract(amount), testKey));
			TransactionInput input = tx2.addInput(spendableOutput);
			Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx2);
		}

		store.resetStore();

		// Again but with more output coins
		try {
			@SuppressWarnings("deprecation")
			ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount.add(amount), testKey));
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput.getValue(), testKey));
			TransactionInput input = tx2.addInput(spendableOutput);
			Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);
			tx2.getOutput(0).getValue().setValue(tx2.getOutput(0).getValue().getValue() + 1);
			createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
					networkParameters.getGenesisBlock(), tx2);
			fail();
		} catch (InvalidTransactionException e) {
		}
	}

	@Test
	public void testSolidityTXOutputNonNegative() throws Exception {
		store.resetStore();

		// Create block with negative outputs
		try {
			@SuppressWarnings("deprecation")
			ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(-1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(
					new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), testKey));
			TransactionInput input = tx2.addInput(spendableOutput);
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
		store.resetStore();

		// Create genesis block
		try {
			Block b = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			b.setBlockType(Type.BLOCKTYPE_INITIAL);
			b.solve();
			blockGraph.add(b, false);
			fail();
		} catch (GenesisBlockDisallowedException e) {
		}
	}

	@Test
	public void testSoliditySigOps() throws Exception {
		store.resetStore();

		// Create block with outputs
		try {
			@SuppressWarnings("deprecation")
			ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
			Transaction tx2 = new Transaction(networkParameters);
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, testKey));
			tx2.addOutput(
					new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), testKey));
			TransactionInput input = tx2.addInput(spendableOutput);

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
	public void testSolidityRewardTxTooClose() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with spending inputs
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);

			fail();
		} catch (InvalidTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWrongDifficulty() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with spending inputs
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		rewardBlock.setDifficultyTarget(rollingBlock.getDifficultyTarget());
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);
			fail();
		} catch (InvalidTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWithTransfers1() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with spending inputs
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		Transaction tx = rewardBlock.getTransactions().get(0);

		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		List<UTXO> outputs = getBalance(false, testKey);
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
				0);
		Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
		tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
		tx.addOutput(
				new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
		TransactionInput input = tx.addInput(spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

		TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);

			fail();
		} catch (TransactionOutputsDisallowedException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWithTransfers2() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with additional tx
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		Transaction tx = createTestGenesisTransaction();
		rewardBlock.addTransaction(tx);
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);

			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityRewardTxWithMissingRewardInfo() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with malformed tx data
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		rewardBlock.getTransactions().get(0).setData(null);
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);

			fail();
		} catch (MissingTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityRewardTxMalformedData1() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with malformed tx data
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		rewardBlock.getTransactions().get(0).setData(new byte[] { 2, 3, 4 });
		;
		rewardBlock.solve();

		// Should not go through
		try {
			blockGraph.add(rewardBlock, false);

			fail();
		} catch (MalformedTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityRewardTxMalformedData2() throws Exception {
		store.resetStore();
		Block rollingBlock = networkParameters.getGenesisBlock();

		// Generate blocks until passing first reward interval
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true);
		}

		// Generate mining reward block with malformed fields
		Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
				rollingBlock.getHash(), rollingBlock.getHash());
		Block testBlock1 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock2 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock3 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock4 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock5 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock6 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		Block testBlock7 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
		RewardInfo rewardInfo1 = RewardInfo.parse(testBlock1.getTransactions().get(0).getData());
		RewardInfo rewardInfo2 = RewardInfo.parse(testBlock2.getTransactions().get(0).getData());
		RewardInfo rewardInfo3 = RewardInfo.parse(testBlock3.getTransactions().get(0).getData());
		RewardInfo rewardInfo4 = RewardInfo.parse(testBlock4.getTransactions().get(0).getData());
		RewardInfo rewardInfo5 = RewardInfo.parse(testBlock5.getTransactions().get(0).getData());
		RewardInfo rewardInfo6 = RewardInfo.parse(testBlock6.getTransactions().get(0).getData());
		RewardInfo rewardInfo7 = RewardInfo.parse(testBlock7.getTransactions().get(0).getData());
		rewardInfo1.setFromHeight(-1);
		rewardInfo2.setPrevRewardHash(null);
		rewardInfo3.setPrevRewardHash(getRandomSha256Hash());
		rewardInfo4.setPrevRewardHash(rollingBlock.getHash());
		rewardInfo5.setPrevRewardHash(rollingBlock.getHash());
		rewardInfo6.setToHeight(12341324);
		rewardInfo7.setToHeight(-1);
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

		// Should not go through
		try {
			blockGraph.add(testBlock1, false);
			fail();
		} catch (InvalidTransactionDataException e) {
		}
		try {
			blockGraph.add(testBlock2, false);
			fail();
		} catch (MissingDependencyException e) {
		}
		if (blockGraph.add(testBlock3, false))
			fail();
		try {
			blockGraph.add(testBlock4, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
		try {
			blockGraph.add(testBlock5, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
		try {
			blockGraph.add(testBlock6, false);
			fail();
		} catch (InvalidTransactionDataException e) {
		}
		try {
			blockGraph.add(testBlock7, false);
			fail();
		} catch (InvalidTransactionDataException e) {
		}
	}

	interface TestCase {
		public boolean expectsException();

		public void preApply(TokenInfo info);
	}

	@Test
	public void testSolidityTokenMalformedData1() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
		block.getTransactions().get(0).setData(null);

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);
			fail();
		} catch (MissingTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedData2() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
		block.getTransactions().get(0).setData(new byte[] { 1, 2 });

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);
			fail();
		} catch (MalformedTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedDataSignature1() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
		block.getTransactions().get(0).setDataSignature(null);

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);
			fail();
		} catch (MissingTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMalformedDataSignature2() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase without data
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
		block.getTransactions().get(0).setDataSignature(new byte[] { 1, 2 });

		// solve block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);
			fail();
		} catch (MalformedTransactionDataException e) {
		}
	}

	@Test
	public void testSolidityTokenMutatedData() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(1);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo0 = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo0.setToken(tokens);
		tokenInfo0.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		TestCase[] executors = new TestCase[] { new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {
				tokenInfo5.setToken(null);
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {
				tokenInfo5.setMultiSignAddresses(null);
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {
				tokenInfo5.getToken().setAmount(-1);
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setBlockhash(null);
			}

			@Override
			public boolean expectsException() {
				return false;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setDescription(null);
			}

			@Override
			public boolean expectsException() {
				return false;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setDescription(
						new String(new char[NetworkParameters.TOKEN_MAX_DESC_LENGTH]).replace("\0", "A"));
			}

			@Override
			public boolean expectsException() {
				return false;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setDescription(
						new String(new char[NetworkParameters.TOKEN_MAX_DESC_LENGTH + 1]).replace("\0", "A"));
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenstop(false);
			}

			@Override
			public boolean expectsException() {
				return false;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {
				tokenInfo5.getToken().setPrevblockhash(null);
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setPrevblockhash("test");
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setPrevblockhash(getRandomSha256Hash().toString());
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setPrevblockhash(networkParameters.getGenesisBlock().getHashAsString());
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
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
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenid(null);
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenid("");
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenid("test");
			}

			@Override
			public boolean expectsException() {
				return true;
			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenid(Utils.HEX.encode(testKey.getPubKey()));
			}

			@Override
			public boolean expectsException() {
				return true;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenindex(-1);
			}

			@Override
			public boolean expectsException() {
				return true;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenindex(5);
			}

			@Override
			public boolean expectsException() {
				return true;

			}
		}, new TestCase() { // 20
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenindex(NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER + 1);
			}

			@Override
			public boolean expectsException() {
				return true;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenname(null);
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenname("");
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken()
						.setTokenname(new String(new char[NetworkParameters.TOKEN_MAX_NAME_LENGTH]).replace("\0", "A"));
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setTokenname(
						new String(new char[NetworkParameters.TOKEN_MAX_NAME_LENGTH + 1]).replace("\0", "A"));
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
				tokenInfo5.getToken().setUrl(null);
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken().setUrl("");
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getToken()
						.setUrl(new String(new char[NetworkParameters.TOKEN_MAX_URL_LENGTH]).replace("\0", "A"));
			}

			@Override
			public boolean expectsException() {
				return false;

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) { // 30

				tokenInfo5.getToken()
						.setUrl(new String(new char[NetworkParameters.TOKEN_MAX_URL_LENGTH + 1]).replace("\0", "A"));
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

				tokenInfo5.getMultiSignAddresses().get(0).setAddress(new String(new char[222]).replace("\0", "A"));
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
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0).setBlockhash("test");
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0).setBlockhash(getRandomSha256Hash().toString());
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0)
						.setBlockhash(networkParameters.getGenesisBlock().getHashAsString());
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(-1);
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) { // 40

				tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(0);
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(4);
			}

			@Override
			public boolean expectsException() {
				return false; // these do not matter, they are overwritten

			}
		}, new TestCase() {
			@Override
			public void preApply(TokenInfo tokenInfo5) {

				tokenInfo5.getMultiSignAddresses().get(0).setPubKeyHex(Utils.HEX.encode(walletKeys.get(8).getPubKey()));
			}

			@Override
			public boolean expectsException() {
				return false;

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
			public void preApply(TokenInfo tokenInfo5) {

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
			TokenInfo tokenInfo = TokenInfo.parse(tokenInfo0.toByteArray());
			executors[i].preApply(tokenInfo);

			// Make block including it
			Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

			// Coinbase with signatures
			if (tokenInfo.getMultiSignAddresses() != null) {

				block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
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
				MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
				transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
			}

			// solve block
			block.solve();

			// Should not go through
			if (executors[i].expectsException()) {
				try {
					blockGraph.add(block, false);
					fail("Number " + i + " failed");
				} catch (VerificationException e) {
				}
			} else {
				if (!blockGraph.add(block, false))
					fail("Number " + i + " failed");
			}
		}
	}

	@Test
	public void testSolidityTokenMutatedDataSignatures() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
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
		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

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
			blockGraph.add(block1, false);
			fail();
		} catch (MissingSignatureException e) {
		}
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (MissingSignatureException e) {
		}

		try {
			blockGraph.add(block3, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block4, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block5, false);
		} catch (VerificationException e) {
			fail();
		}

		try {
			blockGraph.add(block6, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block7, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block8, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block9, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block10, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block11, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block12, false);
			fail();
		} catch (VerificationException e) {
		}
		try {
			blockGraph.add(block13, false);
			fail();
		} catch (VerificationException e) {
		}

		try {
			blockGraph.add(block14, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block15, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block16, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block17, false);
		} catch (VerificationException e) {
			fail();
		}
		try {
			blockGraph.add(block18, false);
		} catch (VerificationException e) {
			fail();
		}
	}

	@Test
	public void testSolidityTokenNoTransaction() throws Exception {
		store.resetStore();

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);
			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityTokenMultipleTransactions1() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
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
		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

		// Add another transfer transaction
		Transaction tx = createTestGenesisTransaction();
		block.addTransaction(tx);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);

			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityTokenMultipleTransactions2() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
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
		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

		// Add transaction again
		block.addTransaction(transaction);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);

			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityTokenTransferTransaction() throws Exception {
		store.resetStore();

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Add transfer transaction
		Transaction tx = createTestGenesisTransaction();
		block.addTransaction(tx);

		// save block
		block.solve();

		// Should not go through
		try {
			blockGraph.add(block, false);

			fail();
		} catch (NotCoinbaseException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorWrongTokenid() throws JsonProcessingException, Exception {
		store.resetStore();

		// Generate an eligible issuance
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				false);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate a subsequent issuance that does not work
		byte[] pubKey2 = walletKeys.get(8).getPubKey();
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey2);
		long amount2 = coinbase2.getValue();
		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey2), "Test",
				"Test", 1, 1, amount2, true);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", walletKeys.get(8).getPublicKeyAsHex()));
		try {
			Wallet r = walletAppKit.wallet();
			Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
			blockGraph.add(block, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityTokenWrongTokenindex() throws JsonProcessingException, Exception {
		store.resetStore();
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				false);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);
		long amount2 = coinbase2.getValue();
		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
				"Test", 1, 2, amount2, true);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {
			Wallet r = walletAppKit.wallet();
			Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
			blockGraph.add(block, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorStopped() throws JsonProcessingException, Exception {
		store.resetStore();
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);
		long amount2 = coinbase2.getValue();
		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
				"Test", 1, 1, amount2, true);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {
			Wallet r = walletAppKit.wallet();
			Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
			blockGraph.add(block, false);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorConflictingType() throws JsonProcessingException, Exception {
		store.resetStore();
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				false);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);
		long amount2 = coinbase2.getValue();
		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
				"Test", 1, 1, amount2, true);
		tokens2.setTokentype(123);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {
			Wallet r = walletAppKit.wallet();
			Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
			blockGraph.add(block, false);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenPredecessorConflictingName() throws JsonProcessingException, Exception {
		store.resetStore();
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();

		// Generate an eligible issuance
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				false);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
		Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

		// Generate a subsequent issuance that does not work
		TokenInfo tokenInfo2 = new TokenInfo();
		Coin coinbase2 = Coin.valueOf(666, pubKey);
		long amount2 = coinbase2.getValue();
		Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test2",
				"Test", 1, 1, amount2, true);
		tokenInfo2.setToken(tokens2);
		tokenInfo2.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
		try {
			Wallet r = walletAppKit.wallet();
			Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
			blockGraph.add(block, false);
			fail();
		} catch (PreviousTokenDisallowsException e) {
		}
	}

	@Test
	public void testSolidityTokenWrongTokenCoinbase() throws Exception {
		store.resetStore();

		// Generate an eligible issuance tokenInfo
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();
		Coin coinbase = Coin.valueOf(77777L, pubKey);
		long amount = coinbase.getValue() + 2;
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
				true);
		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		// Make block including it
		Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

		// Coinbase with signatures
		block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
		Transaction transaction = block.getTransactions().get(0);

		// Add another output for other tokens
		block.getTransactions().get(0).addOutput(Coin.SATOSHI.times(2), outKey.toAddress(networkParameters));

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
			blockGraph.add(block, false);

			fail();
		} catch (InvalidTokenOutputException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenOk() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should go through
		assertTrue(blockGraph.add(block1, false));
	}

	@Test
	public void testSolidityOrderOpenNoTransactions() throws Exception {
		store.resetStore();

		Block block1 = null;
		{
			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenMultipleTXs() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
			long amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, amount, true);

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
			blockGraph.confirm(tokenBlock.getHash(), new HashSet<>());
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey).stream()
					.filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
							.equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			Transaction tx2 = new Transaction(networkParameters);
			// Create burning 2 "test"
			List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters,
					outputs2.get(0), 0);
			Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount2, testKey));
			tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput2.getValue().subtract(amount2),
					testKey));
			TransactionInput input2 = tx2.addInput(spendableOutput2);
			Sha256Hash sighash2 = tx2.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig2 = new TransactionSignature(testKey.sign(sighash2), Transaction.SigHash.ALL,
					false);
			Script inputScript2 = ScriptBuilder.createInputScript(sig2);
			input2.setScriptSig(inputScript2);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.addTransaction(tx2);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (IncorrectTransactionCountException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenNoTokensOffered() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenMultipleTokens() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
			long amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, amount, true);

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
			blockGraph.confirm(tokenBlock.getHash(), new HashSet<>());
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey).stream()
					.filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
							.equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));

			// Create burning 2 "test"
			List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters,
					outputs2.get(0), 0);
			Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount2, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput2.getValue().subtract(amount2),
					testKey));

			TransactionInput input = tx.addInput(spendableOutput);
			TransactionInput input2 = tx.addInput(spendableOutput2);

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
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenNoBIGs() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
			long amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, amount, true);

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
			blockGraph.confirm(tokenBlock.getHash(), new HashSet<>());
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test2", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 "test"
			List<UTXO> outputs2 = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput2 = new FreeStandingTransactionOutput(this.networkParameters,
					outputs2.get(0), 0);
			Coin amount2 = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount2, testKey));
			tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput2.getValue().subtract(amount2),
					testKey));

			TransactionInput input2 = tx.addInput(spendableOutput2);

			Sha256Hash sighash2 = tx.hashForSignature(0, spendableOutput2.getScriptBytes(), Transaction.SigHash.ALL,
					false);
			TransactionSignature sig2 = new TransactionSignature(testKey.sign(sighash2), Transaction.SigHash.ALL,
					false);
			Script inputScript2 = ScriptBuilder.createInputScript(sig2);
			input2.setScriptSig(inputScript2);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpenFractionalPrice() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Make the "test" token
		Block tokenBlock = null;
		{
			TokenInfo tokenInfo = new TokenInfo();

			Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
			long amount = coinbase.getValue();
			Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
					1, 0, amount, true);

			tokenInfo.setToken(tokens);
			tokenInfo.getMultiSignAddresses()
					.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

			// This (saveBlock) calls milestoneUpdate currently
			tokenBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
			blockGraph.confirm(tokenBlock.getHash(), new HashSet<>());
		}

		Block block1 = null;
		{
			// Make a buy order for "test"s with Price 0.5
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(4, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey).stream()
					.filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
							.equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block1, false);
			fail();
		} catch (InvalidOrderException e) {
		}

		Block block2 = null;
		{
			// Make a buy order for "BIG"s with Price 0.5
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(1, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 "test"s
			List<UTXO> outputs = getBalance(false, testKey).stream().filter(
					out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
					.collect(Collectors.toList());
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, testKey.getPubKey());
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block2 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (InvalidOrderException e) {
		}
	}

	@Test
	public void testSolidityOrderOpOk() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should go through
		assertTrue(blockGraph.add(block1, false));

		Block block2 = null;
		{
			// Make an order op
			Transaction tx = new Transaction(networkParameters);
			OrderOpInfo info = new OrderOpInfo(OrderOp.CANCEL, 0, block1.getHash());
			tx.setData(info.toByteArray());

			// Legitimate it by signing
			Sha256Hash sighash1 = tx.getHash();
			ECKey.ECDSASignature party1Signature = testKey.sign(sighash1, null);
			byte[] buf1 = party1Signature.encodeToDER();
			tx.setDataSignature(buf1);

			// Create block with order
			block2 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_OP);
			block2.solve();
		}

		// Should go through
		assertTrue(blockGraph.add(block2, false));
	}

	@Test
	public void testSolidityOrderOpWrongSig() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
		}

		// Should go through
		assertTrue(blockGraph.add(block1, false));

		Block block2 = null;
		{
			// Make an order op
			Transaction tx = new Transaction(networkParameters);
			OrderOpInfo info = new OrderOpInfo(OrderOp.CANCEL, 0, block1.getHash());
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
			block2 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_OP);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (VerificationException e) {
		}
	}

	@Test
	public void testSolidityOrderReclaimOk() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			this.blockGraph.add(block1, true);
		}

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());
		Block fusingBlock = rewardBlock1.createNextBlock(block1);
		blockGraph.add(fusingBlock, false);

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rewardBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = fusingBlock.createNextBlock(fusingBlock);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
		}

		// Should go through
		assertTrue(this.blockGraph.add(block2, false));
	}

	@Test
	public void testSolidityOrderReclaimInvalidDependencyOrder() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			this.blockGraph.add(block1, true);
		}

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());
		Block fusingBlock = rewardBlock1.createNextBlock(block1);
		blockGraph.add(fusingBlock, false);

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, rollingBlock1.getHash(), rewardBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = fusingBlock.createNextBlock(fusingBlock);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityOrderReclaimInvalidDependencyOrderMatching() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			this.blockGraph.add(block1, true);
		}

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());
		Block fusingBlock = rewardBlock1.createNextBlock(block1);
		blockGraph.add(fusingBlock, false);

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rollingBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = fusingBlock.createNextBlock(fusingBlock);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}

	@Test
	public void testSolidityOrderReclaimInvalidDependencyOrderMatchingWrongHeight() throws Exception {
		store.resetStore();
		@SuppressWarnings("deprecation")
		ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

		// Generate blocks until passing first reward interval
		Block rollingBlock1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
				+ 1; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			blockGraph.add(rollingBlock1, true);
		}

		// Generate mining reward block
		Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
				networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

		Block block1 = null;
		{
			// Make a buy order for "test"s
			Transaction tx = new Transaction(networkParameters);
			OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
					testKey.toAddress(networkParameters).toBase58());
			tx.setData(info.toByteArray());

			// Create burning 2 BIG
			List<UTXO> outputs = getBalance(false, testKey);
			TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
					outputs.get(0), 0);
			Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
			// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
			// amount, testKey));
			tx.addOutput(
					new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
			TransactionInput input = tx.addInput(spendableOutput);
			Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
					false);

			TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
			Script inputScript = ScriptBuilder.createInputScript(sig);
			input.setScriptSig(inputScript);

			// Create block with order
			block1 = rewardBlock1.createNextBlock(rewardBlock1);
			block1.addTransaction(tx);
			block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
			block1.solve();
			this.blockGraph.add(block1, true);
		}

		// Try order reclaim
		Block block2 = null;
		{
			Transaction tx = new Transaction(networkParameters);
			OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rollingBlock1.getHash());
			tx.setData(info.toByteArray());

			// Create block with order reclaim
			block2 = block1.createNextBlock(block1);
			block2.addTransaction(tx);
			block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
			block2.solve();
		}

		// Should not go through
		try {
			blockGraph.add(block2, false);
			fail();
		} catch (InvalidDependencyException e) {
		}
	}
}