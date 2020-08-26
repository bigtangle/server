/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
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
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MCMCServiceTest extends AbstractIntegrationTest {

    // Test forward cutoff
    // TODO check this test correct? @Test
    public void testForwardCutoff() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(NetworkParameters.FORWARD_BLOCK_HORIZON + 10,
                networkParameters.getGenesisBlock(), blocksAddedAll);

        // MCMC should not update this far out
        mcmcServiceUpdate();
        assertFalse(blockService.getBlockEvaluation(rollingBlock1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockMCMC(rollingBlock1.getHash(), store).getRating() == 0);

        // Reward block should include it
        final Pair<Sha256Hash, Sha256Hash> validatedRewardBlockPair = tipsService
                .getValidatedRewardBlockPair(networkParameters.getGenesisBlock().getHash(), store);
        rewardService.createReward(networkParameters.getGenesisBlock().getHash(), validatedRewardBlockPair.getLeft(),
                validatedRewardBlockPair.getRight(), store);
        assertTrue(blockService.getBlockEvaluation(rollingBlock1.getHash(), store).getMilestone() == 1);
    }

    @Test
    public void testConflictTransactionalUTXO() throws Exception {

        mcmcServiceUpdate();

        // Generate two conflicting blocks
        ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);

        blockGraph.add(b1, true, store);
        blockGraph.add(b2, true, store);

        createAndAddNextBlock(b1, b2);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
    }

    @Test
    public void testConflictReward() throws Exception {

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true, store);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < 1 + 1 + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true, store);
        }

        // Generate eligible mining reward blocks
        Block b1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(),
                rollingBlock1.getHash(), store);
        blockGraph.updateChain();
        Block b2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(),
                rollingBlock1.getHash(), store);
        blockGraph.updateChain();
        syncBlockService.connectingOrphans(store);
        createAndAddNextBlock(b2, b1);

        mcmcServiceUpdate();
        Thread.sleep(2000);
        // assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
        // );
        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());

    }

    @Test
    public void testConflictSameTokenSubsequentIssuance() throws Exception {

        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);
        mcmcServiceUpdate();
        // Generate two subsequent issuances

        Block conflictBlock1, conflictBlock2;
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);

            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            conflictBlock1 = saveTokenUnitTestWithTokenname(tokenInfo2, coinbase2, outKey, null);
        }
        mcmcServiceUpdate();
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);

            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            conflictBlock2 = saveTokenUnitTestWithTokenname(tokenInfo2, coinbase2, outKey, null);
        }

        mcmcServiceUpdate();

        BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(conflictBlock1.getHash(), store);
        BlockEvaluation blockEvaluation2 = blockService.getBlockEvaluation(conflictBlock2.getHash(), store);

        assertFalse(blockEvaluation.isConfirmed() && blockEvaluation2.isConfirmed());
        assertTrue(blockEvaluation.isConfirmed() || blockEvaluation2.isConfirmed());

        mcmcServiceUpdate();

        assertFalse(blockEvaluation.isConfirmed() && blockEvaluation2.isConfirmed());
        assertTrue(blockEvaluation.isConfirmed() || blockEvaluation2.isConfirmed());
    }

    @Test
    public void testConflictSameTokenidSubsequentIssuance() throws Exception {

        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);

        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test", "Test", 1,
                1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock1 = saveTokenUnitTestWithTokenname(tokenInfo2, coinbase2, outKey, null);
        mcmcServiceUpdate();

        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);

        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test", "Test", 1,
                1, coinbase3.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo3.setToken(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock2 = saveTokenUnitTestWithTokenname(tokenInfo3, coinbase3, outKey, null);

        // Make a fusing block
        Block rollingBlock = conflictBlock1.createNextBlock(conflictBlock2);
        blockGraph.add(rollingBlock, true, store);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(conflictBlock2.getHash(), store).isConfirmed());
 
    }

    @Test
    public void testConflictSameTokenFirstIssuance() throws Exception {

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Make another conflicting issuance that goes through
        // Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true, store);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
    }

    @Test
    public void testConflictSameTokenidFirstIssuance() throws Exception {

        // Generate an issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        mcmcServiceUpdate();

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);

        Token tokens2 = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0,
                coinbase2.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true, store);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
    }

    @Test
    public void testUpdateConflictingTransactionalMilestoneCandidates() throws Exception {

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b3 = createAndAddNextBlock(b1, b2);
        for (int i = 0; i < 15; i++) {
            createAndAddNextBlock(b3, b3);
        }
        createAndAddNextBlock(b2, b2);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
    }

    @Test
    public void testUpdateConflictingTokenMilestoneCandidates() throws Exception {

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        mcmcServiceUpdate();

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Make another conflicting issuance that goes through
        Block genHash = networkParameters.getGenesisBlock();
        Block block2 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true, store);

        // Let block 1 win
        createAndAddNextBlock(block1, block2);

        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed());

        // Reorg to block 2
        rollingBlock = block2;
        for (int i = 0; i < 25; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true, store);
        }

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash(), store).isConfirmed());
    }

    @Test
    public void testUpdateConflictingConsensusMilestoneCandidates() throws Exception {

        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 2 * 1 + 1 + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true, store);
        }

        // Generate mining reward blocks
        Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash(), store);
        blockGraph.updateChain();
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash(), store);
        blockGraph.updateChain();
        createAndAddNextBlock(rewardBlock1, rewardBlock2);

        // One of them shall win
        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());
    }

    @Test
    public void testUpdate() throws Exception {

        Block b1 = createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        Block b2 = createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        Block b3 = createAndAddNextBlock(b1, b2);
        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash(), store).isConfirmed());

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        // b5 and b8
        Block b5 = createAndAddNextBlockWithTransaction(b3, b3, doublespendTX);
        Block b5link = createAndAddNextBlock(b5, b5);
        Block b6 = createAndAddNextBlock(b3, b3);
        Block b7 = createAndAddNextBlock(b3, b3);
        Block b8 = createAndAddNextBlockWithTransaction(b6, b7, doublespendTX);
        Block b8link = createAndAddNextBlock(b8, b8);
        Block b9 = createAndAddNextBlock(b5link, b6);
        Block b10 = createAndAddNextBlock(b9, b8link);
        Block b11 = createAndAddNextBlock(b9, b8link);
        Block b12 = createAndAddNextBlock(b5link, b8link);
        Block b13 = createAndAddNextBlock(b5link, b8link);
        Block b14 = createAndAddNextBlock(b5link, b8link);
        Block bOrphan1 = createAndAddNextBlock(b1, b1);
        Block bOrphan5 = createAndAddNextBlock(b5link, b5link);
        // syncBlockService.updateSolidity();
        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b12.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash(), store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash(),store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash(),store).isConfirmed());

        // Now make block 8 heavier and higher rated than b5 to make it
        // disconnect block
        // 5+link and connect block 8+link instead
        Block b8weight1 = createAndAddNextBlock(b8link, b8link);
        Block b8weight2 = createAndAddNextBlock(b8link, b8link);
        Block b8weight3 = createAndAddNextBlock(b8link, b8link);
        Block b8weight4 = createAndAddNextBlock(b8link, b8link);

        // extra weights to ensure this works
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);

        mcmcServiceUpdate();
        blockGraph.updateChain();
        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash(), store).isConfirmed());
        // sometimes this won't work since probabilistic. this is tested later
        // with additional weights
        // assertFalse();
        // assertFalse(blockService.getBlockEvaluation(b5link.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b12.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash(), store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash(),store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash(),store).isConfirmed());
        // assertTrue(
        // blockService.getBlockEvaluation(b8weight1.getHash(),store).isConfirmed());
       // assertTrue(blockService.getBlockEvaluation(b8weight2.getHash(), store).isConfirmed());
       // assertTrue(blockService.getBlockEvaluation(b8weight3.getHash(), store).isConfirmed());
       // assertTrue(blockService.getBlockEvaluation(b8weight4.getHash(), store).isConfirmed());

        // Lastly, there will be a milestone-candidate conflict in the last
        // update that
        // should not change anything
        mcmcServiceUpdate();

        assertTrue(blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash(), store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash(),store).isConfirmed());
        // assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash(),store).isConfirmed());
        // assertTrue(blockService.getBlockEvaluation(b8weight1.getHash(),store).isConfirmed());
        // assertTrue(blockService.getBlockEvaluation(b8weight2.getHash(),store).isConfirmed());
        // assertTrue(blockService.getBlockEvaluation(b8weight3.getHash(),store).isConfirmed());
        // assertTrue(blockService.getBlockEvaluation(b8weight4.getHash(),store).isConfirmed());

        // Check heights (handmade tests)
        assertEquals(0,
                blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash(), store).getHeight());
        assertEquals(1, blockService.getBlockEvaluation(b1.getHash(), store).getHeight());
        assertEquals(1, blockService.getBlockEvaluation(b2.getHash(), store).getHeight());
        assertEquals(2, blockService.getBlockEvaluation(b3.getHash(), store).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b5.getHash(), store).getHeight());
        assertEquals(4, blockService.getBlockEvaluation(b5link.getHash(), store).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b6.getHash(), store).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b7.getHash(), store).getHeight());
        assertEquals(4, blockService.getBlockEvaluation(b8.getHash(), store).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(b8link.getHash(), store).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(b9.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b10.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b11.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b12.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b13.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b14.getHash(), store).getHeight());
        assertEquals(2, blockService.getBlockEvaluation(bOrphan1.getHash(), store).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(bOrphan5.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight1.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight2.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight3.getHash(), store).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight4.getHash(), store).getHeight());

        // Check depths (handmade tests)
        assertEquals(5, blockService.getBlockMCMC(b1.getHash(), store).getDepth());
        assertEquals(5, blockService.getBlockMCMC(b2.getHash(), store).getDepth());
        assertEquals(4, blockService.getBlockMCMC(b3.getHash(), store).getDepth());
        assertEquals(3, blockService.getBlockMCMC(b5.getHash(), store).getDepth());
        assertEquals(2, blockService.getBlockMCMC(b5link.getHash(), store).getDepth());
        assertEquals(3, blockService.getBlockMCMC(b6.getHash(), store).getDepth());
        assertEquals(3, blockService.getBlockMCMC(b7.getHash(), store).getDepth());
        assertEquals(2, blockService.getBlockMCMC(b8.getHash(), store).getDepth());
        assertEquals(1, blockService.getBlockMCMC(b8link.getHash(), store).getDepth());
        assertEquals(1, blockService.getBlockMCMC(b9.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b10.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b11.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b12.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b13.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b14.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(bOrphan1.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(bOrphan5.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b8weight1.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b8weight2.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b8weight3.getHash(), store).getDepth());
        assertEquals(0, blockService.getBlockMCMC(b8weight4.getHash(), store).getDepth());

        // Check cumulative weights (handmade tests)
        assertEquals(28, blockService.getBlockMCMC(b1.getHash(), store).getCumulativeWeight());
        assertEquals(27, blockService.getBlockMCMC(b2.getHash(), store).getCumulativeWeight());
        assertEquals(26, blockService.getBlockMCMC(b3.getHash(), store).getCumulativeWeight());
        assertEquals(9, blockService.getBlockMCMC(b5.getHash(), store).getCumulativeWeight());
        assertEquals(8, blockService.getBlockMCMC(b5link.getHash(), store).getCumulativeWeight());
        assertEquals(21, blockService.getBlockMCMC(b6.getHash(), store).getCumulativeWeight());
        assertEquals(20, blockService.getBlockMCMC(b7.getHash(), store).getCumulativeWeight());
        assertEquals(19, blockService.getBlockMCMC(b8.getHash(), store).getCumulativeWeight());
        assertEquals(18, blockService.getBlockMCMC(b8link.getHash(), store).getCumulativeWeight());
        assertEquals(3, blockService.getBlockMCMC(b9.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b10.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b11.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b12.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b13.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b14.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(bOrphan1.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(bOrphan5.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b8weight1.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b8weight2.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b8weight3.getHash(), store).getCumulativeWeight());
        assertEquals(1, blockService.getBlockMCMC(b8weight4.getHash(), store).getCumulativeWeight());

        // Make consensus block
        Block rollingBlock = b8link;
        for (int i = 0; i < 1; i++) {
            rollingBlock = createAndAddNextBlock(rollingBlock, rollingBlock);
        }
        rewardService.createReward(networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(),
                rollingBlock.getHash(), store);

        mcmcServiceUpdate();

        assertFalse(blockService.getBlockEvaluation(b5.getHash(), store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b6.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b7.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8.getHash(), store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash(), store).isConfirmed());
    }

    @Test
    public void testReorgToken() throws Exception {

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                coinbase.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        mcmcServiceUpdate();

        // Should go through
        assertTrue(blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
        Transaction tx1 = block1.getTransactions().get(0);
        assertTrue(store.getTransactionOutput(block1.getHash(), tx1.getHash(), 0).isConfirmed());
        assertTrue(store.getTokenConfirmed(block1.getHash()));

        // Remove it from the confirmed
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 1; i < 35; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true, store);
            mcmcServiceUpdate();

        }

        //TODO mcmc deterministic  Should be out
     //   assertFalse(blockService.getBlockEvaluation(block1.getHash(), store).isConfirmed());
     //   assertFalse(store.getTransactionOutput(block1.getHash(), tx1.getHash(), 0).isConfirmed());
      //  assertFalse(store.getTokenConfirmed(block1.getHash()));
    }

}