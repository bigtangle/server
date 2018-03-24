/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.MySQLFullPrunedBlockChainTest;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockGraph;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bignetcoin.server.config.GlobalConfigurationProperties;
import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InitTest extends AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(InitTest.class);
    @Autowired
    private NetworkParameters networkParameters;
    @Autowired
    private MilestoneService milestoneService;

    @Autowired
    private BlockService blockService;

   

    @Test
    public void testInitWallet() throws Exception {
        KeyParameter aesKey = null;
        WalletAppKit bitcoin = new WalletAppKit(PARAMS, new File("../bignetcoin-wallet"), "bignetcoin");
        bitcoin.wallet().setServerURL(contextRoot);

        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), aesKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            System.out.println(
                    "realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", prvKey : " + ecKey.getPrivateKeyAsHex());
            keys.add(ecKey);
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                System.out.println(
                        "realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", priKey : " + ecKey.getPrivateKeyAsHex());
                keys.add(ecKey);
            }
        }
        ECKey outKey = new ECKey();
        int height = 1;

        // Add genesis block
        blockgraph.add(networkParameters.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService
                .getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();

        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        milestoneService.update();

        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, networkParameters.getGenesisBlock().getHash());
        System.out.println("rollingBlock : " + rollingBlock.getHashAsString());
        rollingBlock = networkParameters.getDefaultSerializer().makeBlock(rollingBlock.bitcoinSerialize());
        System.out.println("rollingBlock : " + rollingBlock.getHashAsString());

        // Create bitcoin spend of 1 BTC.
        Wallet wallet = bitcoin.wallet();
        ECKey myKey = keys.get(0);
        System.out.println("key " + myKey.getPublicKeyAsHex());

        Coin amount = Coin.valueOf(100000, NetworkParameters.BIGNETCOIN_TOKENID);
        // Address address = new Address(PARAMS, toKey.getPubKeyHash());

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, myKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);

        milestoneService.update(); // ADDED

        // byte[] data = getAskTransactionBlock();
        // ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        // Block r1 = nextBlockSerializer(byteBuffer);
        // Block r2 = nextBlockSerializer(byteBuffer);

        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        // rollingBlock = BlockForTest.createNextBlock(r1, null, r2.getHash());

        amount = Coin.valueOf(100, NetworkParameters.BIGNETCOIN_TOKENID);

        ECKey toKey = new ECKey();
        Address address = new Address(networkParameters, toKey.getPubKeyHash());
        SendRequest request = SendRequest.to(address, amount);
        request.changeAddress = new Address(networkParameters, myKey.getPubKeyHash());
        wallet.completeTx(request);

        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);

        // cal block update
        milestoneService.update();
        // logger.info("AVAILABLE : " +
        // wallet.getBalance(Wallet.BalanceType.AVAILABLE) + ", ESTIMATED : " +
        // wallet.getBalance(Wallet.BalanceType.ESTIMATED));

        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                .content(myKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + response);
    }

}