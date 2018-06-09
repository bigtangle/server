/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;

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
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class APIIntegrationTests extends AbstractIntegrationTest {
    @Autowired
    private NetworkParameters networkParameters;
    private int height = 1;

    private static final Logger logger = LoggerFactory.getLogger(APIIntegrationTests.class);

    @Test
    public void testPayMultiSignToStore() throws BlockStoreException {
        ECKey outKey = new ECKey();
        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
        payMultiSign.setToaddress(outKey.toAddress(networkParameters).toBase58());
        //payMultiSign.setBlockhash(outKey.getPubKey());
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
        
        List<PayMultiSignAddress> payMultiSignAddresses = this.store.getPayMultiSignAddressWithOrderid(payMultiSign.getOrderid());
        assertEquals(payMultiSignAddresses.get(0).getPubKey(), payMultiSignAddress.getPubKey());
        
        this.store.updatePayMultiSignAddressSign(payMultiSign.getOrderid(), outKey.getPublicKeyAsHex(), 1, outKey.getPubKey());
        payMultiSignAddresses = this.store.getPayMultiSignAddressWithOrderid(payMultiSign.getOrderid());
        assertEquals(payMultiSignAddresses.get(0).getSign(), 1);
        
        ECKey ecKey = new ECKey();
        this.store.updatePayMultiSignBlockhash(payMultiSign.getOrderid(), ecKey.getPubKey());
        
        payMultiSign_ = this.store.getPayMultiSignWithOrderid(payMultiSign.getOrderid());
        //assertArrayEquals(payMultiSign_.getBlockhash(), ecKey.getPubKey());
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
    }

    // @Before
    public Block getRollingBlock(ECKey outKey) throws Exception {
        Context.propagate(new Context(networkParameters));
        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        return rollingBlock;
    }

    @Test  (expected = RuntimeException.class)
    public void testCreateTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        // no solve get error code
        
         String r = OkHttp3Util.post(contextRoot +  ReqCmd.saveBlock.name(),
                Json.jsonmapper().writeValueAsString(block.bitcoinSerialize()).getBytes("UTF-8"));

       
    }



    public ECKey createWalletAndAddCoin() throws Exception, PrunedException {
        ECKey outKey = new ECKey();
        Block rollingBlock = this.getRollingBlock(outKey);

        rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,
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

  
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateMultiSigList() throws Exception {
        for (int i = 0; i < 1; i++) {
            testCreateMultiSig();
        }
        
        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(walletKeys.get(0));
        ecKeys.add(walletKeys.get(1));
        ecKeys.add(walletKeys.get(2));
        
        ECKey outKey = new ECKey();
        String tokenid = walletAppKit.wallet().walletKeys(null).get(5).getPublicKeyAsHex();
        Coin amount = Coin.parseCoin("12", Utils.HEX.decode(tokenid));
        
        List<UTXO> outputs = testTransactionAndGetBalances(false, ecKeys);
        

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0), 0);
        Script multisigScript = multisigOutput.getScriptPubKey();
        Transaction transaction = new Transaction(networkParameters);
        transaction.addOutput(amount, outKey);
        TransactionInput input = transaction.addInput(multisigOutput);

        
        PayMultiSign payMultiSign = new PayMultiSign();
        payMultiSign.setOrderid(UUIDUtil.randomUUID());
        payMultiSign.setTokenid(tokenid);
        payMultiSign.setBlockhashHex(Utils.HEX.encode(transaction.bitcoinSerialize()));
        payMultiSign.setToaddress(outKey.toAddress(networkParameters).toBase58());
        payMultiSign.setAmount(amount.getValue());
        payMultiSign.setMinsignnumber(3);
        payMultiSign.setOutpusHashHex(outputs.get(0).getHashHex());

        OkHttp3Util.post(contextRoot + "launchPayMultiSign", Json.jsonmapper().writeValueAsString(payMultiSign));
        
        HashMap<String, Object> requestParam00 = new HashMap<String, Object>();
        requestParam00.put("hexStr", outputs.get(0).getHashHex());
        String resp0 = OkHttp3Util.postString(contextRoot + "outpusWithHexStr", Json.jsonmapper().writeValueAsString(requestParam00));
        System.out.println(resp0);
        



//List<TransactionSignature>        transactionSignatures = new ArrayList<TransactionSignature>();
//List<byte[]> aaa = new ArrayList<>();

        for (ECKey ecKey : ecKeys) {
            List<String> pubKeys = new ArrayList<String>();
            pubKeys.add(ecKey.getPublicKeyAsHex());
            String resp = OkHttp3Util.postString(contextRoot + "getPayMultiSignList", Json.jsonmapper().writeValueAsString(pubKeys));

            HashMap<String, Object> data = Json.jsonmapper().readValue(resp, HashMap.class);
            List<HashMap<String, Object>> payMultiSigns = (List<HashMap<String, Object>>) data.get("payMultiSigns");
            HashMap<String, Object> payMultiSign_ = payMultiSigns.get(0);
            
            byte[] payloadBytes = Utils.HEX.decode((String) payMultiSign_.get("blockhashHex"));
            Transaction transaction0 = networkParameters.getDefaultSerializer().makeTransaction(payloadBytes);
            
            Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript, Transaction.SigHash.ALL, false);
            TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash), Transaction.SigHash.ALL, false);
            
//            transactionSignatures.add(transactionSignature);
//            aaa.add(transactionSignature.encodeToBitcoin());
            
            ECKey.ECDSASignature party1Signature = ecKey.sign(transaction0.getHash());
            byte[] buf1 = party1Signature.encodeToDER();
            
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("orderid", (String) payMultiSign_.get("orderid"));
            requestParam.put("pubKey", ecKey.getPublicKeyAsHex());
            requestParam.put("signature", Utils.HEX.encode(buf1));
            requestParam.put("signInputData", Utils.HEX.encode(transactionSignature.encodeToBitcoin()));
            System.out.println(" " + Utils.HEX.encode(transactionSignature.encodeToBitcoin()));
            resp = OkHttp3Util.postString(contextRoot + "payMultiSign", Json.jsonmapper().writeValueAsString(requestParam));
            System.out.println(resp);
            
            
            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            boolean success = (boolean) result.get("success");
            if (success) {
                requestParam.clear();
                requestParam.put("orderid", (String) payMultiSign_.get("orderid"));
                resp = OkHttp3Util.postString(contextRoot + "getPayMultiSignAddressList", Json.jsonmapper().writeValueAsString(requestParam));
                System.out.println(resp);
                result = Json.jsonmapper().readValue(resp, HashMap.class);
                List<HashMap<String, Object>> payMultiSignAddresses = (List<HashMap<String, Object>>) result.get("payMultiSignAddresses");
                List<HashMap<String, Object>> payMultiSignAddresses0 = new ArrayList<HashMap<String, Object>>();
                for (ECKey ecKey0 : ecKeys) {
                    for (HashMap<String, Object> payMultiSignAddress : payMultiSignAddresses) {
                        if (ecKey0.getPublicKeyAsHex().equals(payMultiSignAddress.get("pubKey"))) {
                            payMultiSignAddresses0.add(payMultiSignAddress);
                            break;
                        }
                    }
                }
                List<byte[]> sigs = new ArrayList<byte[]>();
                for (HashMap<String, Object> payMultiSignAddress : payMultiSignAddresses0) {
                    String signInputDataHex = (String) payMultiSignAddress.get("signInputDataHex");
                    sigs.add(Utils.HEX.decode(signInputDataHex));
                }
////                TransactionInput input = transaction0.getInput(0);
//
//                Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs, null);
//                input.setScriptSig(inputScript);
//                
//                byte[] buf = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
//                Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
//                rollingBlock.addTransaction(transaction0);
//                rollingBlock.solve();
//               checkResponse( OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize()));
//                Script inputScript = ScriptBuilder.createMultiSigInputScript(transactionSignatures);
                Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs);
                transaction0.getInput(0).setScriptSig(inputScript);
                
                byte[] buf = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
                Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
                rollingBlock.addTransaction(transaction0);
                rollingBlock.solve();
               checkResponse( OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize()));
            }
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiSigTokenIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
     
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        final TokenInfo tokenInfo = null;
        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenInfoIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        final TokenInfo tokenInfo = new TokenInfo();
        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenInfoSignnumberZero() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", -1, true,
                true, true);
        tokenInfo.setTokens(tokens);

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigTokenSerialIsNull() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigMultiSignAddressSizeZero() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = Utils.HEX.encode(keys.get(0).getPubKeyHash());
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    // @Test
    public void testMultiSigMultiSignAddressSizeSignnumber() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = keys.get(0).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiSigMultiSignDatasignatureAddress() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        // Tokenid with keys.get(0) is already used in setup
        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        // int duration = (Integer) result2.get("errorcode");
        // System.out.println("resp : " + resp);
        // assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                Json.jsonmapper().writeValueAsString(requestParam0));
        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);

        List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
        byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDatasignatire() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
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

        transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
        resp = OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 101);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = RuntimeException.class)
    public void testMultiSigMultiSignSignatureError() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        
        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        // int duration = (Integer) result2.get("errorcode");
        // System.out.println("resp : " + resp);
        // assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                Json.jsonmapper().writeValueAsString(requestParam0));
        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);

        List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
        byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDatasignatire() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
        }
        byte[] buf1 = new byte[] { (byte) 0, (byte) 1, (byte) 2, (byte) 3 };

        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenid);
        multiSignBy0.setTokenindex(tokenindex_);
        multiSignBy0.setAddress(keys.get(0).toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(keys.get(0).getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);

        transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
        resp = OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 100);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testMultiSigMultiSignSignatureSuccess() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        String tokenid = keys.get(5).getPublicKeyAsHex();
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(0).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(1).getPublicKeyAsHex()));
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", keys.get(2).getPublicKeyAsHex()));

        block.addCoinbaseTransaction(keys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();
        String resp = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 0);

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", keys.get(0).toAddress(networkParameters).toBase58());
        resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                Json.jsonmapper().writeValueAsString(requestParam0));
        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);

        List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
        byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);
        List<MultiSignBy> multiSignBies = null;
        if (transaction.getDatasignatire() == null) {
            multiSignBies = new ArrayList<MultiSignBy>();
        } else {
            multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
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

        transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
        resp = OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize());

        result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        duration = (Integer) result2.get("errorcode");
        System.out.println("resp : " + resp);
        assertEquals(duration, 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSingleTokenIndexCheckTokenExist() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        ECKey outKey = keys.get(6);
        byte[] pubKey = outKey.getPubKey();
        for (int i = 1; i <= 2; i++) {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
            requestParam.put("amount", 100000L);
            requestParam.put("tokenname", "Test");
            requestParam.put("description", "Test");
            requestParam.put("blocktype", true);
            requestParam.put("tokenHex", outKey.getPublicKeyAsHex());
            requestParam.put("signnumber", 3);
            OkHttp3Util.postString(contextRoot + ReqCmd.createGenesisBlock.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            // HashMap<String, Object> result000 =
            // Json.jsonmapper().readValue(resp, HashMap.class);
            // int duration = (Integer) result000.get("errorcode");
            // if (i == 1) {
            // assertEquals(duration, 0);
            // }
            // if (i == 2) {
            // assertEquals(duration, 101);
            // }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateMultiSigTokenIndexCheckTokenExist() throws JsonProcessingException, Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        String tokenid = keys.get(5).getPublicKeyAsHex();
        for (int i = 1; i <= 2; i++) {
            TokenInfo tokenInfo = new TokenInfo();
            Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                    true, false);
            tokenInfo.setTokens(tokens);

            ECKey key1 = keys.get(0);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

            ECKey key2 = keys.get(1);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

            ECKey key3 = keys.get(2);
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));

            int amount = 100000000;
            Coin basecoin = Coin.valueOf(amount, tokenid);

            Integer tokenindex_ = 1;

            tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = networkParameters.getDefaultSerializer().makeBlock(data);
            block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
            block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
            block.solve();

            System.out.println("block hash : " + block.getHashAsString());

            // save block
            String resp000 = OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());
            HashMap result000 = Json.jsonmapper().readValue(resp000, HashMap.class);
            int duration = (Integer) result000.get("errorcode");
            System.out.println("resp : " + resp000);
            if (i == 1) {
                assertEquals(duration, 0);
            }
            if (i == 2) {
                assertEquals(duration, 101);
                return;
            }
            List<ECKey> ecKeys = new ArrayList<ECKey>();
            ecKeys.add(key1);
            ecKeys.add(key2);
            ecKeys.add(key3);

            for (ECKey ecKey : ecKeys) {
                HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
                requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
                String resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                        Json.jsonmapper().writeValueAsString(requestParam0));
                System.out.println(resp);

                HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
                List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
                byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) i - 1).get("blockhashHex"));
                Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
                Transaction transaction = block0.getTransactions().get(0);

                List<MultiSignBy> multiSignBies = null;
                if (transaction.getDatasignatire() == null) {
                    multiSignBies = new ArrayList<MultiSignBy>();
                } else {
                    multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
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

                transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
                resp = OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize());
                System.out.println("resp : " + resp);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void testCreateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);

        String tokenid = keys.get(5).getPublicKeyAsHex();

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, false);
        tokenInfo.setTokens(tokens);

        ECKey key1 = keys.get(0);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        ECKey key3 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));

        int amount = 678900000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");

        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();

        System.out.println("block hash : " + block.getHashAsString());

        // save block
        OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());

        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(key1);
        ecKeys.add(key2);
        ecKeys.add(key3);

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
            String resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
            byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
            Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            Transaction transaction = block0.getTransactions().get(0);

            List<MultiSignBy> multiSignBies = null;
            if (transaction.getDatasignatire() == null) {
                multiSignBies = new ArrayList<MultiSignBy>();
            } else {
                multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignatire(), List.class);
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

            transaction.setDatasignatire(Json.jsonmapper().writeValueAsBytes(multiSignBies));
            checkResponse(OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize()));

        }
        // checkBalance(tokenid, ecKeys );
    }

    @Test
    public void testECKey() {
        ECKey outKey = new ECKey();
        logger.debug( "pubkey= "+ Utils.HEX.encode(outKey.getPubKey()));
     //  ECKey ecKey = ECKey.fromPublicOnly(outKey.getPubKey());
        logger.debug( "pivkey= "+outKey.getPrivateKeyAsHex());
    }

    public void testRequestBlock(Block block) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("createGenesisBlock resp : " + re);

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
          byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(request));
        return data;
    }

    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
             String data =  OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(),
                block.bitcoinSerialize());
        logger.info("testSaveBlock resp : " + data);
        checkResponse(data);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        String tokenid = keys.get(7).getPublicKeyAsHex();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, true);
        tokenInfo.setTokens(tokens);
        ECKey key1 = keys.get(0);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));
        ECKey key2 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));
        ECKey key3 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));
        int amount = 100000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);
        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");
        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();
        // save block
        OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());

        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("address", key1.toAddress(networkParameters).toBase58());
        String resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                Json.jsonmapper().writeValueAsString(requestParam0));

        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
        List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
        byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
        Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
        Transaction transaction = block0.getTransactions().get(0);

        TokenInfo updateTokenInfo = new TokenInfo().parse(transaction.getData());
        updateTokenInfo.getTokens().setTokenname("UPDATE_TOKEN");
        ECKey key4 = keys.get(3);
        updateTokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key4.getPublicKeyAsHex()));
        requestParam = new HashMap<String, String>();
          data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block_ = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
        block_.addCoinbaseTransaction(key4.getPubKey(), basecoin, updateTokenInfo);
        block_.solve();
        // save block
        OkHttp3Util.post(contextRoot + "updateTokenInfo", block_.bitcoinSerialize());

    }

}
