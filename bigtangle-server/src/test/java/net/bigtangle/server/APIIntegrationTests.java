/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatch;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Transaction.SigHash;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class APIIntegrationTests extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;
    private int height = 1;

    private static final Logger logger = LoggerFactory.getLogger(APIIntegrationTests.class);

    @Test
    public void testSaveOrderMatch() throws BlockStoreException {
        OrderMatch orderMatch = new OrderMatch();
        orderMatch.setMatchid(UUID.randomUUID().toString().replaceAll("-", ""));
        orderMatch.setRestingOrderId(UUID.randomUUID().toString().replaceAll("-", ""));
        orderMatch.setIncomingOrderId(UUID.randomUUID().toString().replaceAll("-", ""));
        orderMatch.setType(1);
        orderMatch.setPrice(1000);
        orderMatch.setExecutedQuantity(1);
        orderMatch.setRemainingQuantity(100);
        this.store.saveOrderMatch(orderMatch);
    }

    @Autowired
    private MilestoneService milestoneService;

    // @Autowired
    // private BlockService blockService;

    // @Test
    public void createECKey() {
        ECKey ecKey = new ECKey();
        logger.info("pubKey : " + Utils.HEX.encode(ecKey.getPubKey()));
        logger.info("pubKeyHash : " + Utils.HEX.encode(ecKey.getPubKeyHash()));
        // pubKey =
        // 032f46420523938d355d1a539e849cf2903a314dce13c32562c0dec456757c9dce
        ECKey toKey = ECKey.fromPublicOnly(ecKey.getPubKey());
        logger.info("pubKey : " + Utils.HEX.encode(ecKey.getPubKey()));
        logger.info("pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
    }

    @Test
    public void testWalletWrapperECKey() {
        Wallet wallet = new Wallet(networkParameters, contextRoot);
        for (int i = 0; i < 10; i++) {
            ECKey toKey = wallet.freshReceiveKey();
            logger.info("a->eckey pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
            toKey = wallet.currentReceiveKey();
            logger.info("c->eckey pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
        }
    }

    @Test
    public void testGetTokenById() throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", "b5c5ef754de00444775ef7247d51f48d6e13cbdf");
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("getTokenById resp : " + resp);
    }

    // @Before
    public Block getRollingBlock(ECKey outKey) throws Exception {
        Context.propagate(new Context(networkParameters));
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        return rollingBlock;
    }

    @Test
    public void testCreateTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        reqCmdSaveBlock(block);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTransactionAndGetOutputs() throws Exception {
        ECKey toKey = createWalletAndAddCoin();

        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getOutputs.name())
                .content(toKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + response);

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> outputs0 = (List<Map<String, Object>>) data.get("outputs");
        List<UTXO> outputs = new ArrayList<UTXO>();
        for (Map<String, Object> map : outputs0) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(map);
            outputs.add(utxo);
        }
    }

    public ECKey createWalletAndAddCoin() throws Exception, PrunedException {
        ECKey outKey = new ECKey();
        Block rollingBlock = this.getRollingBlock(outKey);

        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();

        Wallet wallet = new Wallet(networkParameters);
        wallet.setUTXOProvider(store);

        ECKey toKey = wallet.freshReceiveKey();
        Coin amount = Coin.valueOf(100, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);

        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        milestoneService.update();
        return toKey;
    }

    @Test
    public void testSpringBootGetBalances() throws Exception {
        ECKey ecKey = new ECKey();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                .content(ecKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }

    @Test
    public void testSpringBootGetBlockEvaluations() throws Exception {
        ECKey ecKey = new ECKey();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getAllEvaluations.name())
                .content(ecKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }

    @Test
    public void testCreateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, "Test", "Test", "", 3, true, true, true);
        tokenInfo.setTokens(tokens);
        
        ECKey key1 = keys.get(0);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, key1.toAddress(networkParameters).toBase58()));

        ECKey key2 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, key2.toAddress(networkParameters).toBase58()));
        
        ECKey key3 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, key3.toAddress(networkParameters).toBase58()));

        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        tokenInfo.getTokenSerials().add(new TokenSerial(tokenid, 0, 100000000));
        
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();
        
        // save block
        OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        
        requestParam.clear();
        requestParam.put("address", key1.toAddress(networkParameters).toBase58());
        String resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress", Json.jsonmapper().writeValueAsString(requestParam));
        System.out.println(resp);
        
        requestParam.clear();
        requestParam.put("address", key2.toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress", Json.jsonmapper().writeValueAsString(requestParam));
        System.out.println(resp);
        
        requestParam.clear();
        requestParam.put("address", key3.toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress", Json.jsonmapper().writeValueAsString(requestParam));
        System.out.println(resp);
        
        requestParam.clear();
        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));
        data = OkHttp3Util.post(contextRoot + "getBlock", Json.jsonmapper().writeValueAsString(requestParam));
        
        block = networkParameters.getDefaultSerializer().makeBlock(data);
        Transaction transaction = block.getTransactions().get(0);
        
        requestParam.clear();
        data = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block signBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        signBlock.addCoinbaseTransactionData(key1.getPubKey(), basecoin, transaction.getData());

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = key1.sign(sighash);
        ECKey.ECDSASignature party2Signature = key2.sign(sighash);
        ECKey.ECDSASignature party3Signature = key3.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();
        byte[] buf2 = party2Signature.encodeToDER();
        byte[] buf3 = party3Signature.encodeToDER();
        
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenid);
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(key1.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(key1.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        
        MultiSignBy multiSignBy1 = new MultiSignBy();
        multiSignBy1.setTokenid(tokenid);
        multiSignBy1.setTokenindex(0);
        multiSignBy1.setAddress(key2.toAddress(networkParameters).toBase58());
        multiSignBy1.setPublickey(Utils.HEX.encode(key2.getPubKey()));
        multiSignBy1.setSignature(Utils.HEX.encode(buf2));
        multiSignBies.add(multiSignBy1);
        
        MultiSignBy multiSignBy2 = new MultiSignBy();
        multiSignBy2.setTokenid(tokenid);
        multiSignBy2.setTokenindex(0);
        multiSignBy2.setAddress(key3.toAddress(networkParameters).toBase58());
        multiSignBy2.setPublickey(Utils.HEX.encode(key3.getPubKey()));
        multiSignBy2.setSignature(Utils.HEX.encode(buf3));
        multiSignBies.add(multiSignBy2);
        
        byte[] datasignatire = Json.jsonmapper().writeValueAsBytes(multiSignBies);
        signBlock.getTransactions().get(0).setDatasignatire(datasignatire);

        OkHttp3Util.post(contextRoot + "multiSign", signBlock.bitcoinSerialize());

        TransactionSignature party1TransactionSignature = new TransactionSignature(party1Signature, SigHash.ALL, false);
        TransactionSignature party2TransactionSignature = new TransactionSignature(party2Signature, SigHash.ALL, false);
        TransactionSignature party3TransactionSignature = new TransactionSignature(party3Signature, SigHash.ALL, false);
        

    }

//    @Test
//    public void testSpringBootCreateGenesisBlock() throws Exception {
//        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
//        testSpringBootCreateGenesisBlock(keys.get(0));
//        testSpringBootCreateGenesisBlock(keys.get(1));
//    }

    /*public void testSpringBootCreateGenesisBlock(ECKey outKey) throws Exception {
        byte[] pubKey = outKey.getPubKey();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 100000L);
        requestParam.put("tokenname", "Test");
        requestParam.put("description", "Test");
        requestParam.put("blocktype", true);
        requestParam.put("tokenHex", Utils.HEX.encode(outKey.getPubKeyHash()));
        requestParam.put("signnumber", 3);
        OkHttp3Util.post(contextRoot + ReqCmd.createGenesisBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        requestParam.clear();
        requestParam.put("tokenid", Utils.HEX.encode(outKey.getPubKeyHash()));
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultisignaddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("getMultisignaddress resp : " + resp);

        ECKey ecKey = new ECKey();
        requestParam.clear();
        requestParam.put("tokenid", Utils.HEX.encode(outKey.getPubKeyHash()));
        requestParam.put("address", ecKey.toAddress(this.networkParameters).toString());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.addMultisignaddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("addMultisignaddress resp : " + resp);

        requestParam.clear();
        requestParam.put("tokenid", Utils.HEX.encode(outKey.getPubKeyHash()));
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultisignaddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("getMultisignaddress resp : " + resp);

        requestParam.clear();
        requestParam.put("tokenid", Utils.HEX.encode(outKey.getPubKeyHash()));
        requestParam.put("address", ecKey.toAddress(this.networkParameters).toString());
        requestParam.put("tokenindex", 0);
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.multiSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("addMultisignaddress resp : " + resp);

        requestParam.clear();
        requestParam.put("tokenid", Utils.HEX.encode(outKey.getPubKeyHash()));
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultisignaddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        logger.info("getMultisignaddress resp : " + resp);
    }*/

    @Test
    public void testECKey() {
        ECKey outKey = new ECKey();
        System.out.println(Utils.HEX.encode(outKey.getPubKeyHash()));
        ECKey ecKey = ECKey.fromPublicOnly(outKey.getPubKey());
        System.out.println(Utils.HEX.encode(ecKey.getPubKeyHash()));
    }

    public void testRequestBlock(Block block) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("createGenesisBlock resp : " + re);

    }

    @Test
    public void testSpringBootSaveBlock() throws Exception {
        Block block = networkParameters.getGenesisBlock();
        reqCmdSaveBlock(block);
    }

    public Block nextBlockSerializer(ByteBuffer byteBuffer) {
        int len = byteBuffer.getInt();
        byte[] data = new byte[len];
        byteBuffer.get(data);
        Block r1 = networkParameters.getDefaultSerializer().makeBlock(data);
        System.out.println("block len : " + len + " conv : " + r1.getHashAsString());
        return r1;
    }

    public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> request = new HashMap<String, Object>();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.askTransaction.name())
                .content(toJson(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        byte[] data = mvcResult.getResponse().getContentAsByteArray();
        return data;
    }

    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveBlock.name())
                .content(block.bitcoinSerialize());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testSaveBlock resp : " + data);
    }

}
