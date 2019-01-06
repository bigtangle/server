/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.TipsService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ConsensusRuleTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConsensusRuleTest.class);

    @Autowired
    private BlockService blockService;
    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private NetworkParameters networkParameters;
    @Autowired
    private FullPrunedBlockStore store;
    @Autowired
    private TipsService tipsService;
    
    @Autowired
    DBStoreConfiguration dbConfiguration;

    ECKey outKey = new ECKey();

    public Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    private Block createAndAddNextBlock(Block b1, long bVersion, byte[] pubKey, Block b2)
            throws Exception {
        Block block = BlockForTest.createNextBlock(b1, bVersion, b2);
        blockService.saveBlock(block);
        log.debug("created block:" + block.getHashAsString());
        return block;
    }

    private Block createAndAddNextBlockWithTransaction(Block b1, long bVersion, byte[] pubKey, Block b2,
            Transaction prevOut) throws Exception {
        Block block = BlockForTest.createNextBlock(b1, bVersion, b2);
        block.addTransaction(prevOut);
        block.solve();
        blockService.saveBlock(block);
        log.debug("created block:" + block.getHashAsString());
        return block;
    }

    private Transaction makeTestTransaction() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, genesiskey));
        TransactionInput input = doublespendTX.addInput(spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);
    
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        return doublespendTX;
    }
    
    // TODO manual creation of tests via summary consensus rules.md
    // TODO code coverage
    // TODO refactor abstractintegrationtest, other tests etc. 
    
    @Test(expected=VerificationException.class)
    public void testFutureTimestamp() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,  r1);
        b.setTime(1577836800); // 01/01/2020 @ 12:00am (UTC)
        b.solve();
        blockService.saveBlock(b);
    }

    @Test(expected=VerificationException.class)
    public void testIncorrectPoW() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,  r1);
        b.setTime(1377836800);
        blockService.saveBlock(b);
    }

    @Test
    public void testUnsolidBlockAllowed() throws Exception {
        store.resetStore();
        
        Sha256Hash sha256Hash1 = getRandomSha256Hash();
        Sha256Hash sha256Hash2 = getRandomSha256Hash();
        Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
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
        Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
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
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, sha256Hash, sha256Hash, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
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
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, sha256Hash, networkParameters.getGenesisBlock().getHash(), Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
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
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, networkParameters.getGenesisBlock().getHash(), sha256Hash, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
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
        Transaction tx1 = makeTestTransaction();
        Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
        
        milestoneService.update();

        // Create block with dependency
        Transaction tx2 = makeTestTransaction();
        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
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
    
    // TODO continue
    
}