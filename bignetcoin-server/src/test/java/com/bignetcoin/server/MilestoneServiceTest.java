/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MilestoneServiceTest extends AbstractIntegrationTest {
	private static final Logger log = LoggerFactory.getLogger(MilestoneServiceTest.class);

	@Autowired
	private TipsService tipService;

	@Autowired
	private BlockService blockService;

	@Autowired
	private MilestoneService milestoneService;

	ECKey outKey = new ECKey();
	
	private Block createAndAddNextBlockCoinbase(Block b1, long bVersion, byte[] pubKey, Sha256Hash b2) throws VerificationException, PrunedException {
		Block block = BlockForTest.createNextBlockWithCoinbase(b1, bVersion, pubKey, 0, b2);
		this.blockgraph.add(block);
		log.debug("created block:" + block.getHashAsString());
		return block;
	}

	private Block createAndAddNextBlockWithTransaction(Block b1, long bVersion, byte[] pubKey, Sha256Hash b2, Transaction prevOut) throws VerificationException, PrunedException {
		Block block = BlockForTest.createNextBlockWithCoinbase(b1, bVersion, pubKey, 0, b2);
		block.addTransaction(prevOut);
		block.solve();
		this.blockgraph.add(block);
		log.debug("created block:" + block.getHashAsString());
		return block;
	}

	public List<Block> createLinearTangle1() throws Exception {

		Block b0 = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0,
				PARAMS.getGenesisBlock().getHash());
		Block b1 = BlockForTest.createNextBlockWithCoinbase(b0, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, PARAMS.getGenesisBlock().getHash());
		Block b2 = BlockForTest.createNextBlockWithCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, b0.getHash());
		Block b3 = BlockForTest.createNextBlockWithCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, b2.getHash());
		Block b4 = BlockForTest.createNextBlockWithCoinbase(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, b2.getHash());
		Block b5 = BlockForTest.createNextBlockWithCoinbase(b4, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, b1.getHash());
		List<Block> blocks = new ArrayList<Block>();
		blocks.add(b0);
		blocks.add(b1);
		blocks.add(b2);
		blocks.add(b3);
		blocks.add(b4);
		blocks.add(b5);
		int i = 0;
		for (Block block : blocks) {
			this.blockgraph.add(block);
			log.debug("create  " + i + " block:" + block.getHashAsString());
			i++;

		}
		return blocks;
	}
	
    private String CONTEXT_ROOT = "http://localhost:14265/";

	public void createMilestoneTestTangle1() throws Exception {

		// Assumption: minimum depth for milestone is zero
		blockgraph.add(PARAMS.getGenesisBlock());
		BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(PARAMS.getGenesisBlock().getHash());
		blockService.updateMilestone(genesisEvaluation, true);
		blockService.updateSolid(genesisEvaluation, true);
		// TODO connectTransactions of genesisblock too

		Block b1 = createAndAddNextBlockCoinbase(PARAMS.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), PARAMS.getGenesisBlock().getHash());
		Block b2 = createAndAddNextBlockCoinbase(PARAMS.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), PARAMS.getGenesisBlock().getHash());
		Block b3 = createAndAddNextBlockCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b2.getHash());
		milestoneService.update();
		assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
		assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
		assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
		
		//TODO test create conflicting txs
//		ArrayList<ECKey> keys = new ArrayList<ECKey>();
//		keys.add(outKey);
//        Wallet wallet = Wallet.fromKeys(PARAMS, keys);
//        wallet.setServerURL(CONTEXT_ROOT);
//        Coin amount = Coin.parseCoin(amountEdit.getText(), NetworkParameters.BIGNETCOIN_TOKENID);
//        SendRequest request = SendRequest.to(destination, amount);
//        wallet.completeTx(request);

        Transaction transaction = b1.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        Coin amount = Coin.valueOf(100000, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, outKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);

		Block b5 = createAndAddNextBlockWithTransaction(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3.getHash(), t);
		Block b6 = createAndAddNextBlockCoinbase(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3.getHash());
		Block b7 = createAndAddNextBlockCoinbase(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3.getHash());
		Block b8 = createAndAddNextBlockWithTransaction(b6, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b7.getHash(), t);
		Block b8link = createAndAddNextBlockCoinbase(b8, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8.getHash());
		Block b9 = createAndAddNextBlockCoinbase(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b6.getHash());
		Block b10 = createAndAddNextBlockCoinbase(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
		Block b11 = createAndAddNextBlockCoinbase(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
		Block b12 = createAndAddNextBlockCoinbase(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
		Block b13 = createAndAddNextBlockCoinbase(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
		Block b14 = createAndAddNextBlockCoinbase(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
		Block bOrphan1 = createAndAddNextBlockCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b1.getHash());
		Block bOrphan5 = createAndAddNextBlockCoinbase(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b5.getHash());
		milestoneService.update();
		assertTrue(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
		assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
		assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b9.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b10.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b11.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b12.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b13.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(b14.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isMilestone());
		assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isMilestone());
	}

	@Test
	public void testLinearTangle() throws Exception {
		createLinearTangle1();
		milestoneService.update();
	}

	@Test
	public void testMilestoneConflictingCandidates() throws Exception {
		createMilestoneTestTangle1();
		milestoneService.update();
	}

	// @Test
	// public void cumulativweigthLinearBlock() throws Exception {
	// List<Block> re = createLinearBlock ();
	// Map<Sha256Hash, Long> cumulativweigths = new HashMap<Sha256Hash, Long>();
	// tipsManager.recursiveUpdateCumulativeweights(re.get(0).getHash(),
	// cumulativweigths, new HashSet<>());
	// int i = 0;
	// for (Block block : re) {
	// log.debug(" " + i + " block:" + block.getHashAsString() + " cumulativweigth :
	// "
	// + cumulativweigths.get(re.get(i).getHash()));
	// i++;
	// }
	// Iterator<Map.Entry<Sha256Hash, Long>> it =
	// cumulativweigths.entrySet().iterator();
	// while (it.hasNext()) {
	// Map.Entry<Sha256Hash, Long> pair = (Map.Entry<Sha256Hash, Long>) it.next();
	// log.debug("hash : " + pair.getKey() + " -> " + pair.getValue());
	// this.store.updateBlockEvaluationCumulativeweight(pair.getKey(),
	// pair.getValue().intValue());
	// }
	// }
	// @Test
	// public void depth() throws Exception {
	// List<Block> re = createBlock();
	// Map<Sha256Hash, Long> depths = new HashMap<Sha256Hash, Long>();
	// tipsManager.recursiveUpdateDepth(re.get(0).getHash(), depths);
	// int i = 0;
	// for (Block block : re) {
	// log.debug(
	// " " + i + " block:" + block.getHashAsString() + " depth : " +
	// depths.get(re.get(i).getHash()));
	// Sha256Hash blockhash = block.getHash();
	// Long depth = depths.get(re.get(i).getHash());
	// this.store.updateBlockEvaluationDepth(blockhash, depth.intValue());
	// i++;
	// }
	// }
	//
	// @Test
	// public void depth1() throws Exception {
	// List<Block> re = createLinearBlock();
	// Map<Sha256Hash, Long> depths = new HashMap<Sha256Hash, Long>();
	// tipsManager.recursiveUpdateDepth(re.get(0).getHash(), depths);
	// int i = 0;
	// for (Block block : re) {
	// log.debug(
	// " " + i + " block:" + block.getHashAsString() + " depth : " +
	// depths.get(re.get(i).getHash()));
	// Sha256Hash blockhash = block.getHash();
	// Long depth = depths.get(re.get(i).getHash());
	// this.store.updateBlockEvaluationDepth(blockhash, depth.intValue());
	// i++;
	// }
	// }
	// @Test
	// public void updateLinearCumulativeweightsTestWorks() throws Exception {
	// createLinearBlock();
	// Map<Sha256Hash, Set<Sha256Hash>> blockCumulativeweights1 = new
	// HashMap<Sha256Hash, Set<Sha256Hash>>();
	// tipsManager.updateHashCumulativeweights(PARAMS.getGenesisBlock().getHash(),
	// blockCumulativeweights1,
	// new HashSet<>());
	//
	// Iterator<Map.Entry<Sha256Hash, Set<Sha256Hash>>> iterator =
	// blockCumulativeweights1.entrySet().iterator();
	// while (iterator.hasNext()) {
	// Map.Entry<Sha256Hash, Set<Sha256Hash>> pair = (Map.Entry<Sha256Hash,
	// Set<Sha256Hash>>) iterator.next();
	// log.debug(
	// "hash : " + pair.getKey() + " \n size " + pair.getValue().size() + "-> " +
	// pair.getValue());
	// Sha256Hash blockhash = pair.getKey();
	// int cumulativeweight = pair.getValue().size();
	// this.store.updateBlockEvaluationCumulativeweight(blockhash,
	// cumulativeweight);
	// }
	// }
	//
	// @Test
	// public void getBlockToApprove() throws Exception {
	// final SecureRandom random = new SecureRandom();
	// for (int i = 1; i < 20; i++) {
	// Sha256Hash b0Sha256Hash =
	// tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
	// random);
	// Sha256Hash b1Sha256Hash =
	// tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
	// random);
	// log.debug("b0Sha256Hash : " + b0Sha256Hash.toString());
	// log.debug("b1Sha256Hash : " + b1Sha256Hash.toString());
	// }
	// }
	//
	// @Test
	// public void getBlockToApproveTest2() throws Exception {
	// createBlock();
	// ECKey outKey = new ECKey();
	// int height = 1;
	//
	// for (int i = 1; i < 20; i++) {
	// Block r1 = blockService.getBlock(getNextBlockToApprove());
	// Block r2 = blockService.getBlock(getNextBlockToApprove());
	// Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(r2,
	// Block.BLOCK_VERSION_GENESIS,
	// outKey.getPubKey(), 0, r1.getHash());
	// blockgraph.add(rollingBlock);
	// log.debug("create block : " + i + " " + rollingBlock);
	// }
	//
	// }
	//
	// public Sha256Hash getNextBlockToApprove() throws Exception {
	// final SecureRandom random = new SecureRandom();
	// return tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null,
	// 27, 27, random);
	// // Sha256Hash b1Sha256Hash =
	// // tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null,
	// // 27, 27, random);
	//
	// }

}