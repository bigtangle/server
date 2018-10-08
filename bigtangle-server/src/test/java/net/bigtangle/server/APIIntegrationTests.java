/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

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
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetTokensResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.OutputsDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignAddressListResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignDetailsResponse;
import net.bigtangle.core.http.server.resp.PayMultiSignResponse;
import net.bigtangle.core.http.server.resp.SettingResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class APIIntegrationTests extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(APIIntegrationTests.class);

    public void testMultiSignByJson() throws Exception {
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid("111111");
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress("222222");
        multiSignBy0.setPublickey("33333");
        multiSignBy0.setSignature("44444");
        multiSignBies.add(multiSignBy0);

        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);

        String jsonStr = Json.jsonmapper().writeValueAsString(multiSignByRequest);
        logger.info(jsonStr);

        multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
            logger.info(Json.jsonmapper().writeValueAsString(multiSignBy));
        }
    }

    @Autowired
    private NetworkParameters networkParameters;
    private int height = 1;

    private static final Logger logger = LoggerFactory.getLogger(APIIntegrationTests.class);

    @Test
    public void testClientVersion() throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.version.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        SettingResponse settingResponse = Json.jsonmapper().readValue(resp, SettingResponse.class);
        String version = settingResponse.getVersion();
        assertTrue(version.equals("0.3.1"));
    }

    // FIXME @Test
    public void testBlockDamage() throws Exception {
        ECKey outKey = new ECKey();
        ECKey genesiskey = ECKey.fromPublicOnly(Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput transactionOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, outKey));
        TransactionInput input = tx.addInput(transactionOutput);
        Sha256Hash sighash = tx.hashForSignature(0, transactionOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
        rollingBlock.addTransaction(tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        Transaction tx_ = rollingBlock.getTransactions().get(0);
        buf = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(), Json.jsonmapper().writeValueAsString(requestParam));
        rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
        rollingBlock.addTransaction(tx_);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
    }

    // @Test
    public void testPayMultiSignToStore() throws BlockStoreException {
        ECKey outKey = new ECKey();
        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
        payMultiSign.setToaddress(outKey.toAddress(networkParameters).toBase58());
        // payMultiSign.setBlockhash(outKey.getPubKey());
        payMultiSign.setAmount(1000);
        payMultiSign.setMinsignnumber(3);
        this.store.insertPayPayMultiSign(payMultiSign);

        PayMultiSign payMultiSign_ = this.store.getPayMultiSignWithOrderid(payMultiSign.getOrderid());
        assertEquals(payMultiSign.getOrderid(), payMultiSign_.getOrderid());

        PayMultiSignAddress payMultiSignAddress = new PayMultiSignAddress();
        payMultiSignAddress.setOrderid(payMultiSign.getOrderid());
        payMultiSignAddress.setPubKey(outKey.getPublicKeyAsHex());
        payMultiSignAddress.setSign(0);
        this.store.insertPayMultiSignAddress(payMultiSignAddress);

        List<PayMultiSignAddress> payMultiSignAddresses = this.store
                .getPayMultiSignAddressWithOrderid(payMultiSign.getOrderid());
        assertEquals(payMultiSignAddresses.get(0).getPubKey(), payMultiSignAddress.getPubKey());

        this.store.updatePayMultiSignAddressSign(payMultiSign.getOrderid(), outKey.getPublicKeyAsHex(), 1,
                outKey.getPubKey());
        payMultiSignAddresses = this.store.getPayMultiSignAddressWithOrderid(payMultiSign.getOrderid());
        assertEquals(payMultiSignAddresses.get(0).getSign(), 1);

        ECKey ecKey = new ECKey();
        this.store.updatePayMultiSignBlockhash(payMultiSign.getOrderid(), ecKey.getPubKey());

        payMultiSign_ = this.store.getPayMultiSignWithOrderid(payMultiSign.getOrderid());
        // assertArrayEquals(payMultiSign_.getBlockhash(), ecKey.getPubKey());
    }

    @Autowired
    private MilestoneService milestoneService;

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
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        logger.info("getTokensResponse : " + getTokensResponse);
    }

    // @Before
    public Block getRollingBlock(ECKey outKey) throws Exception {
        Context.propagate(new Context(networkParameters));
        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        blockgraph.add(rollingBlock, true);
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    networkParameters.getGenesisBlock());
            blockgraph.add(rollingBlock, true);
        }
        return rollingBlock;
    }

    @Test(expected = RuntimeException.class)
    public void testCreateTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        // no solve get error code

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(),
                Json.jsonmapper().writeValueAsString(block.bitcoinSerialize()).getBytes("UTF-8"));

    }

    @Test
    public void testCreateMultiSigList() throws Exception {
        for (int i = 0; i < 1; i++) {
            testCreateMultiSig();
        }

        PayMultiSign payMultiSign = launchPayMultiSign();

        for (int i = 0; i < 3; i++) {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("orderid", payMultiSign.getOrderid());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));

            PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                    PayMultiSignAddressListResponse.class);
            List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse
                    .getPayMultiSignAddresses();
            assertTrue(payMultiSignAddresses.size() > 0);
            KeyParameter aesKey = null;
            ECKey currentECKey = null;

            for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
                if (payMultiSignAddress.getSign() == 1) {
                    continue;
                }
                for (ECKey ecKey : walletAppKit.wallet().walletKeys(aesKey)) {
                    if (Utils.HEX.encode(ecKey.getPubKey()).equals(payMultiSignAddress.getPubKey())) {
                        currentECKey = ecKey;
                        break;
                    }
                }
            }
            if (currentECKey == null) {
                return;
            }

            this.payMultiSign(currentECKey, payMultiSign.getOrderid(), networkParameters, contextRoot);
        }
    }

    @Test
    public void testCreateMultiSigListA() throws Exception {
        for (int i = 0; i < 1; i++) {
            testCreateMultiSig();
        }
        ECKey outKey = walletKeys.get(0);
        // pay to address outKey, remainder return to walletKeys with multi
        // signs
        PayMultiSign payMultiSign = launchPayMultiSign(outKey);

        // for (int i = 0; i < 3; i++) {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", payMultiSign.getOrderid());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignAddressListResponse.class);
        List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse.getPayMultiSignAddresses();
        assertTrue(payMultiSignAddresses.size() > 0);
        KeyParameter aesKey = null;
        ECKey currentECKey = null;
        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
            if (payMultiSignAddress.getSign() == 1) {
                continue;
            }
            for (ECKey ecKey : signKeys) {
                if (ecKey.toAddress(networkParameters).toString().equals(payMultiSignAddress.getPubKey())) {
                    currentECKey = ecKey;
                    this.payMultiSign(currentECKey, payMultiSign.getOrderid(), networkParameters, contextRoot);
                    break;
                }
            }

        }

        // this is utxo of outKey, which is not a multi signatures
        List<UTXO> utxos = testTransactionAndGetBalances(false, outKey);
        assertTrue(utxos != null && utxos.size() > 0);
        log.debug("===================");
        for (UTXO utxo : utxos) {
            logger.info(utxo.toString());
        }
        PayMultiSign payMultiSign1 = launchPayMultiSign(outKey);

        // for (int i = 0; i < 3; i++) {
        requestParam = new HashMap<String, Object>();
        requestParam.put("orderid", payMultiSign1.getOrderid());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp, PayMultiSignAddressListResponse.class);
        payMultiSignAddresses = payMultiSignAddressListResponse.getPayMultiSignAddresses();
        assertTrue(payMultiSignAddresses.size() > 0);
        aesKey = null;
        currentECKey = null;
        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
            if (payMultiSignAddress.getSign() == 1) {
                continue;
            }
            for (ECKey ecKey : signKeys) {
                if (ecKey.toAddress(networkParameters).toString().equals(payMultiSignAddress.getPubKey())) {
                    currentECKey = ecKey;
                    this.payMultiSign(currentECKey, payMultiSign.getOrderid(), networkParameters, contextRoot);
                    break;
                }
            }

        }

        // }
        utxos = testTransactionAndGetBalances(false, outKey);
        log.debug("111111111111111");
        assertTrue(utxos != null && utxos.size() > 0);
        for (UTXO utxo : utxos) {
            logger.info(utxo.toString());
        }

    }

    private PayMultiSign launchPayMultiSign(ECKey outKey) throws Exception, JsonProcessingException {
        List<ECKey> ecKeys = new ArrayList<ECKey>();
        // ecKeys.add(walletKeys.get(0));
        ecKeys.add(signKeys.get(1));
        ecKeys.add(signKeys.get(2));

        String tokenid = signKeys.get(0).getPublicKeyAsHex();
        Coin amount = Coin.parseCoin("12", Utils.HEX.decode(tokenid));

        UTXO output = testTransactionAndGetBalances(tokenid, false, signKeys);

        // filter the
        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, output, 0);
        Transaction transaction = new Transaction(networkParameters);
        transaction.addOutput(amount, outKey);
        // remainder of utxo goes here with multi sign keys ecKeys
        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, signKeys);
        Coin amount2 = multisigOutput.getValue().subtract(amount);
        transaction.addOutput(amount2, scriptPubKey);

        transaction.addInput(multisigOutput);

        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(tokenid);
        payMultiSign.setBlockhashHex(Utils.HEX.encode(transaction.bitcoinSerialize()));
        payMultiSign.setToaddress(outKey.toAddress(networkParameters).toBase58());
        payMultiSign.setAmount(amount.getValue());
        payMultiSign.setMinsignnumber(3);
        payMultiSign.setOutpusHashHex(output.getHashHex());
        payMultiSign.setOutputsindex(output.getIndex());

        OkHttp3Util.post(contextRoot + ReqCmd.launchPayMultiSign.name(),
                Json.jsonmapper().writeValueAsString(payMultiSign));
        return payMultiSign;
    }

    private PayMultiSign launchPayMultiSign() throws Exception, JsonProcessingException {

        ECKey outKey = new ECKey();
        return launchPayMultiSign(outKey);
    }

    public void payMultiSign(ECKey ecKey, String orderid, NetworkParameters networkParameters, String contextRoot)
            throws Exception {
        List<String> pubKeys = new ArrayList<String>();
        pubKeys.add(ecKey.getPublicKeyAsHex());

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.clear();
        requestParam.put("orderid", orderid);
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSignDetails.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        PayMultiSignDetailsResponse payMultiSignDetailsResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignDetailsResponse.class);
        PayMultiSign payMultiSign_ = payMultiSignDetailsResponse.getPayMultiSign();

        requestParam.clear();
        requestParam.put("hexStr", payMultiSign_.getOutpusHashHex() + ":" + payMultiSign_.getOutputsindex());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputWithKey.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.debug(resp);

        OutputsDetailsResponse outputsDetailsResponse = Json.jsonmapper().readValue(resp, OutputsDetailsResponse.class);
        UTXO u = outputsDetailsResponse.getOutputs();

        TransactionOutput multisigOutput_1 = new FreeStandingTransactionOutput(networkParameters, u, 0);
        Script multisigScript_1 = multisigOutput_1.getScriptPubKey();

        byte[] payloadBytes = Utils.HEX.decode((String) payMultiSign_.getBlockhashHex());
        Transaction transaction0 = networkParameters.getDefaultSerializer().makeTransaction(payloadBytes);

        Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript_1, Transaction.SigHash.ALL, false);
        TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash),
                Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature party1Signature = ecKey.sign(transaction0.getHash());
        byte[] buf1 = party1Signature.encodeToDER();

        requestParam.clear();
        requestParam.put("orderid", (String) payMultiSign_.getOrderid());
        requestParam.put("pubKey", ecKey.getPublicKeyAsHex());
        requestParam.put("signature", Utils.HEX.encode(buf1));
        requestParam.put("signInputData", Utils.HEX.encode(transactionSignature.encodeToBitcoin()));
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.payMultiSign.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.debug(resp);

        PayMultiSignResponse payMultiSignResponse = Json.jsonmapper().readValue(resp, PayMultiSignResponse.class);
        boolean success = payMultiSignResponse.isSuccess();
        if (success) {
            PayMultiSave(networkParameters, contextRoot, requestParam, payMultiSign_, transaction0);
        }
    }

    private void PayMultiSave(NetworkParameters networkParameters, String contextRoot,
            HashMap<String, Object> requestParam, PayMultiSign payMultiSign_, Transaction transaction0)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        String resp;
        requestParam.clear();
        requestParam.put("orderid", (String) payMultiSign_.getOrderid());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getPayMultiSignAddressList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.debug(resp);

        PayMultiSignAddressListResponse payMultiSignAddressListResponse = Json.jsonmapper().readValue(resp,
                PayMultiSignAddressListResponse.class);
        List<PayMultiSignAddress> payMultiSignAddresses = payMultiSignAddressListResponse.getPayMultiSignAddresses();

        List<TransactionSignature> sigs = new ArrayList<TransactionSignature>();
        for (PayMultiSignAddress payMultiSignAddress : payMultiSignAddresses) {
            String signInputDataHex = payMultiSignAddress.getSignInputDataHex();
            TransactionSignature sig = TransactionSignature.decodeFromBitcoin(Utils.HEX.decode(signInputDataHex), true,
                    false);
            sigs.add(sig);
        }

        Script inputScript = ScriptBuilder.createMultiSigInputScript(sigs);
        transaction0.getInput(0).setScriptSig(inputScript);

        byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
        rollingBlock.addTransaction(transaction0);
        rollingBlock.solve();
        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = RuntimeException.class)
    public void testMultiSigTokenIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);

        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        final TokenInfo tokenInfo = null;
        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenInfoIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        final TokenInfo tokenInfo = new TokenInfo();
        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenInfoSignnumberZero() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), -1, 0, amount, true, false);
        tokenInfo.setTokens(tokens);

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenSerialIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, 0, amount, true, false);
        tokenInfo.setTokens(tokens);

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigMultiSignAddressSizeZero() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();
        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigMultiSignAddressSizeSignnumber() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = keys.get(0).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = RuntimeException.class)
    public void testMultiSigMultiSignDatasignatureAddress() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        // Tokenid with keys.get(0) is already used in setup
        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        // int duration = (Integer) result2.get("errorcode");
        // log.debug("resp : " + resp);
        // assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        String blockhashHex = multiSignResponse.getMultiSigns().get((int) tokenindex_ - 1).getBlockhashHex();
        byte[] payloadBytes = Utils.HEX.decode(blockhashHex);
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);

        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = keys.get(3).sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenid);
        multiSignBy0.setTokenindex(tokenindex_);
        multiSignBy0.setAddress(keys.get(3).toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(keys.get(3).getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = RuntimeException.class)
    public void testMultiSigMultiSignSignatureError() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);

        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        // int duration = (Integer) result2.get("errorcode");
        // log.debug("resp : " + resp);
        // assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        byte[] payloadBytes = Utils.HEX
                .decode((String) multiSignResponse.getMultiSigns().get((int) tokenindex_ - 1).getBlockhashHex());
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);

        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        byte[] buf1 = new byte[] { (byte) 0, (byte) 1, (byte) 2, (byte) 3 };

        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenid);
        multiSignBy0.setTokenindex(tokenindex_);
        multiSignBy0.setAddress(keys.get(0).toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(keys.get(0).getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertTrue(duration == 101);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiSigMultiSignSignatureSuccess() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        byte[] payloadBytes = Utils.HEX
                .decode((String) multiSignResponse.getMultiSigns().get((int) tokenindex_ - 1).getBlockhashHex());
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);

        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDataSignature() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                    MultiSignByRequest.class);
            multiSignBies = multiSignByRequest.getMultiSignBies();
        }
        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = keys.get(0).sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenid);
        multiSignBy0.setTokenindex(tokenindex_);
        multiSignBy0.setAddress(keys.get(0).toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(keys.get(0).getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        duration = (Integer) result2.get("errorcode");
        log.debug("resp : " + resp);
        assertEquals(duration, 0);
    }

    @Test
    public void testCreateSingleTokenIndexCheckTokenExist() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        ECKey outKey = keys.get(6);
        byte[] pubKey = outKey.getPubKey();
        String tokenid = Utils.HEX.encode(pubKey);
        for (int i = 1; i <= 2; i++) {
            TokenInfo tokenInfo = new TokenInfo();

            Coin basecoin = Coin.valueOf(100000L, pubKey);
            long amount = basecoin.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), 1, 0, amount, true, false);
            tokenInfo.setTokens(tokens);

            // add MultiSignAddress item
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = networkParameters.getDefaultSerializer().makeBlock(data);
            block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
            block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo);

            Transaction transaction = block.getTransactions().get(0);

            Sha256Hash sighash = transaction.getHash();
            ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
            byte[] buf1 = party1Signature.encodeToDER();

            List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
            multiSignBy0.setTokenindex(0);
            multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);
            MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
            transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

            // save block
            block.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());

        }
    }

    // TODO fix index previous @Test
    @SuppressWarnings("unchecked")
    public void testCreateMultiSigTokenIndexCheckTokenExist() throws JsonProcessingException, Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        String tokenid = keys.get(3).getPublicKeyAsHex();
        for (int i = 1; i <= 2; i++) {
            TokenInfo tokenInfo = new TokenInfo();

            int amount = 100000000;
            Coin basecoin = Coin.valueOf(amount, tokenid);

            Integer tokenindex_ = 1;
            Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
            tokenInfo.setTokens(tokens);

            ECKey key1 = keys.get(0);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

            ECKey key2 = keys.get(1);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

            ECKey key3 = keys.get(2);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));

            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = networkParameters.getDefaultSerializer().makeBlock(data);
            block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
            block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
            block.solve();

            log.debug("block hash : " + block.getHashAsString());

            // save block
            String resp000 = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
            HashMap<String, Object> result000 = Json.jsonmapper().readValue(resp000, HashMap.class);
            int duration = (Integer) result000.get("errorcode");
            log.debug("resp : " + resp000);
            if (i == 1) {
                assertEquals(duration, 0);
            }
            if (i == 2) {
                // TODO logic is the check of double is in checkSolid
                assertEquals(duration, 0);
                return;
            }
            List<ECKey> ecKeys = new ArrayList<ECKey>();
            ecKeys.add(key1);
            ecKeys.add(key2);
            ecKeys.add(key3);

            for (ECKey ecKey : ecKeys) {
                HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
                requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
                String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                        Json.jsonmapper().writeValueAsString(requestParam0));
                log.debug(resp);

                MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
                String blockhashHex = multiSignResponse.getMultiSigns().get((int) i - 1).getBlockhashHex();
                byte[] payloadBytes = Utils.HEX.decode(blockhashHex);

                Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
                Transaction transaction = block0.getTransactions().get(0);

                List<MultiSignBy> multiSignBies = null;
                if (transaction.getDataSignature() == null) {
                    multiSignBies = new ArrayList<MultiSignBy>();
                } else {
                    MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                            MultiSignByRequest.class);
                    multiSignBies = multiSignByRequest.getMultiSignBies();
                }
                Sha256Hash sighash = transaction.getHash();
                ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
                byte[] buf1 = party1Signature.encodeToDER();

                MultiSignBy multiSignBy0 = new MultiSignBy();
                multiSignBy0.setTokenid(tokenid);
                multiSignBy0.setTokenindex(tokenindex_);
                multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
                multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
                multiSignBy0.setSignature(Utils.HEX.encode(buf1));
                multiSignBies.add(multiSignBy0);
                MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
                transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
                resp = OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize());
                log.debug("resp : " + resp);
            }
        }
    }

    @Test
    public void testCreateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        wallet1();
        wallet2();
        signKeys = new LinkedList<ECKey>();
        signKeys.add(walletAppKit.wallet().walletKeys(null).get(0));
        signKeys.add(walletAppKit1.wallet().walletKeys(null).get(0));
        signKeys.add(walletAppKit2.wallet().walletKeys(null).get(0));

        testCreateMultiSigToken(signKeys);
    }

    @Test
    public void testECKey() {
        ECKey outKey = new ECKey();
        logger.debug("pubkey= " + Utils.HEX.encode(outKey.getPubKey()));
        // ECKey ecKey = ECKey.fromPublicOnly(outKey.getPubKey());
        logger.debug("pivkey= " + outKey.getPrivateKeyAsHex());
    }

    public void testRequestBlock(Block block) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info(" resp : " + re);

    }

    public Block nextBlockSerializer(ByteBuffer byteBuffer) {
        int len = byteBuffer.getInt();
        byte[] data = new byte[len];
        byteBuffer.get(data);
        Block r1 = networkParameters.getDefaultSerializer().makeBlock(data);
        log.debug("block len : " + len + " conv : " + r1.getHashAsString());
        return r1;
    }

    public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> request = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(request));
        return data;
    }

    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
        String data = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        logger.info("testSaveBlock resp : " + data);
        checkResponse(data);
    }

    @Test
    public void testUpdateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        String tokenid = keys.get(7).getPublicKeyAsHex();
        TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        Integer tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 3, tokenindex_, amount, true, false);
        tokenInfo.setTokens(tokens);

        ECKey key1 = keys.get(0);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        ECKey key3 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();
        // save block
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", key1.toAddress(networkParameters).toBase58());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        byte[] payloadBytes = Utils.HEX
                .decode((String) multiSignResponse.getMultiSigns().get((int) tokenindex_).getBlockhashHex());
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);

        Transaction transaction = block0.getTransactions().get(0);

        TokenInfo updateTokenInfo = new TokenInfo().parse(transaction.getData());
        updateTokenInfo.getTokens().setTokenname("UPDATE_TOKEN");
        ECKey key4 = keys.get(3);
        updateTokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key4.getPublicKeyAsHex()));
        requestParam = new HashMap<String, String>();
        data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(), Json.jsonmapper().writeValueAsString(requestParam));
        Block block_ = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.BLOCKTYPE_TOKEN_CREATION);
        block_.addCoinbaseTransaction(key4.getPubKey(), basecoin, updateTokenInfo);
        block_.solve();
        // save block
        OkHttp3Util.post(contextRoot + ReqCmd.updateTokenInfo.name(), block_.bitcoinSerialize());

    }

}
