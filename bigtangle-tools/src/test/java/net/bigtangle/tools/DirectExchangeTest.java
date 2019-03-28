/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOS;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.UserDataResponse;
import net.bigtangle.core.http.server.resp.VOSExecuteListResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

public class DirectExchangeTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DirectExchangeTest.class);

    @Test
    public void testTransactionResolveSubtangleID() throws Exception {
        Transaction transaction = new Transaction(this.networkParameters);

        byte[] subtangleID = new byte[32];
        new Random().nextBytes(subtangleID);

        transaction.setToAddressInSubtangle(subtangleID);

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_CROSSTANGLE);
        block.addTransaction(transaction);
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));
        data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        block = networkParameters.getDefaultSerializer().makeBlock(data);

        Transaction transaction2 = block.getTransactions().get(0);
        assertNotNull(subtangleID);
        assertTrue(Arrays.equals(subtangleID, transaction.getToAddressInSubtangle()));
        assertTrue(Arrays.equals(subtangleID, transaction2.getToAddressInSubtangle()));
    }

    public void createTokenSubtangle() throws Exception {
        ECKey ecKey = new ECKey();
        byte[] pubKey = ecKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Token tokens = Token.buildSubtangleTokenInfo(false, "", Utils.HEX.encode(pubKey), "subtangle", "", "");
        tokenInfo.setToken(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", ecKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0L, pubKey);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(ecKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        // save block
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testWalletImportKeyGiveMoney() throws Exception {
        Wallet coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(
                new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);

        ECKey outKey = new ECKey();

        for (int i = 0; i < 3; i++) {
            Transaction transaction = new Transaction(this.networkParameters);
            Coin amount = Coin.parseCoin("10000", NetworkParameters.BIGTANGLE_TOKENID);
            transaction.addOutput(amount, outKey);

            SendRequest request = SendRequest.forTx(transaction);
            coinbaseWallet.completeTx(request, null);

            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip,
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
            rollingBlock.addTransaction(request.tx);
            rollingBlock.solve();

            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

            List<TransactionOutput> candidates = coinbaseWallet.calculateAllSpendCandidates(null, false);
            for (TransactionOutput transactionOutput : candidates) {
                log.info("UTXO : " + transactionOutput);
            }

            for (UTXO output : this.getBalance(true, outKey)) {
                log.info("UTXO : " + output);
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testWalletBatchGiveMoney() throws Exception {
        Wallet coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(
                new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);

        Transaction transaction = new Transaction(this.networkParameters);
        for (int i = 0; i < 3; i++) {
            ECKey outKey = new ECKey();
            Coin amount = Coin.parseCoin("3", NetworkParameters.BIGTANGLE_TOKENID);
            transaction.addOutput(amount, outKey);
        }

        SendRequest request = SendRequest.forTx(transaction);
        coinbaseWallet.completeTx(request, null);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip, Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        List<TransactionOutput> candidates = coinbaseWallet.calculateAllSpendCandidates(null, false);
        for (TransactionOutput transactionOutput : candidates) {
            log.info("UTXO : " + transactionOutput);
        }
        /*
         * for (UTXO output : this.testTransactionAndGetBalances(true, outKey))
         * { log.info("UTXO : logtput); }
         */
    }

    @Test
    public void searchBlock() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        List<String> address = new ArrayList<String>();
        for (ECKey ecKey : keys) {
            address.add(ecKey.toAddress(networkParameters).toBase58());
        }
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", address);

        String response = OkHttp3Util.post(contextRoot + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        log.info("searchBlock resp : " + response);

    }

    @SuppressWarnings("deprecation")
    @Test
    public void exchangeToken() throws Exception {

        wallet1();
        wallet2();

        // create token
        // get token from wallet to spent
        ECKey yourKey = walletAppKit1.wallet().walletKeys(null).get(0);
        log.debug("toKey : " + yourKey.toAddress(networkParameters).toBase58());
        testCreateToken();
        payToken(yourKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(yourKey);
        List<UTXO> utxos = getBalance(false, keys);
        UTXO yourutxo = utxos.get(0);
        List<UTXO> ulist = getBalance();
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                myutxo = u;
            }
        }
        log.debug("outKey : " + myutxo.getAddress());

        Coin amount = Coin.valueOf(10000, yourutxo.getValue().getTokenid());
        SendRequest req = SendRequest.to(new Address(networkParameters, myutxo.getAddress()), amount);
        req.tx.addOutput(myutxo.getValue(), new Address(networkParameters, yourutxo.getAddress()));

        log.debug(myutxo.getAddress() + ", " + myutxo.getValue());
        log.debug(yourutxo.getAddress() + ", " + amount);

        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        ulist.addAll(utxos);
        walletAppKit.wallet().completeTx(req, walletAppKit.wallet().transforSpendCandidates(ulist), false);
        walletAppKit.wallet().signTransaction(req);

        byte[] a = req.tx.bitcoinSerialize();

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);

        SendRequest request = SendRequest.forTx(transaction);
        walletAppKit1.wallet().signTransaction(request);
        exchangeTokenComplete(request.tx);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", "fromAddress");

        // String response = OkHttp3Util.post(contextRoot +
        // OrdermatchReqCmd.getExchange.name(),
        // Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        // log.info("getExchange resp : " + requestParam);
    }

    @Test
    public void testExchangeTokenMultiManyPart() throws Exception {
        // 5 token 1
        // 2 token sign1 sign2 3 sign2 , sing4

        // BiG system
        //

    }

    @Test
    public void testExchangeTokenMulti() throws Exception {

        wallet1();
        wallet2();

        List<ECKey> keys = walletAppKit1.wallet().walletKeys(null);

        testCreateMultiSigToken(keys.get(0), "test");
        UTXO multitemp = null;
        UTXO systemcoin = null;
        List<UTXO> utxos = getBalance(false, keys);
        for (UTXO utxo : utxos) {
            if (multitemp == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                multitemp = utxo;
            }
            if (systemcoin == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                systemcoin = utxo;
            }
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        UTXO yourutxo = utxos.get(0);
        List<UTXO> ulist = getBalance();
        UTXO mymultitemp = null;
        UTXO mysystemcoin = null;
        for (UTXO utxo : ulist) {
            if (mymultitemp == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                mymultitemp = utxo;
            }
            if (mysystemcoin == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                mysystemcoin = utxo;
            }
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                myutxo = u;
            }
        }
        log.debug("outKey : " + myutxo.getAddress());

        Coin amount = Coin.valueOf(10000, yourutxo.getValue().getTokenid());

        SendRequest req = null;

        // ulist.addAll(utxos);
        Transaction transaction = new Transaction(networkParameters);

        List<ECKey> signKeys = new ArrayList<>();
        signKeys.add(keys.get(0));
        signKeys.add(keys.get(1));
        signKeys.add(keys.get(2));

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, yourutxo, 0);

        transaction.addOutput(amount, Address.fromBase58(networkParameters, myutxo.getAddress()));

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(3, signKeys);
        Coin amount2 = multisigOutput.getValue().subtract(amount);
        transaction.addOutput(amount2, scriptPubKey);

        transaction.addInput(multisigOutput);

        List<byte[]> sigs = new ArrayList<byte[]>();
        for (ECKey ecKey : signKeys) {
            TransactionOutput multisigOutput_ = new FreeStandingTransactionOutput(networkParameters, yourutxo, 0);
            Script multisigScript_ = multisigOutput_.getScriptPubKey();

            Sha256Hash sighash = transaction.hashForSignature(0, multisigScript_, Transaction.SigHash.ALL, false);
            TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash, null),
                    Transaction.SigHash.ALL, false);

            ECKey.ECDSASignature party1Signature = ecKey.sign(transaction.getHash(), null);
            byte[] signature = party1Signature.encodeToDER();
            boolean success = ECKey.verify(transaction.getHash().getBytes(), signature, ecKey.getPubKey());
            if (!success) {
                throw new BlockStoreException("key multisign signature error");
            }
            sigs.add(transactionSignature.encodeToBitcoin());
        }
        Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs);
        transaction.getInput(0).setScriptSig(inputScript);
        req = SendRequest.forTx(transaction);

        exchangeTokenComplete(req.tx);

        for (UTXO utxo : getBalance(false, keys)) {
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        for (UTXO utxo : getBalance()) {
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        Address destination = Address.fromBase58(networkParameters, yourutxo.getAddress());
        amount = Coin.valueOf(1000, myutxo.getValue().getTokenid());
        req = SendRequest.to(destination, amount);
        walletAppKit.wallet().completeTx(req, null);
        walletAppKit.wallet().signTransaction(req);

        exchangeTokenComplete(req.tx);
        UTXO multitemp1 = null;
        UTXO systemcoin1 = null;
        for (UTXO utxo : getBalance(false, keys)) {
            if (multitemp1 == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                multitemp1 = utxo;
            }
            if (systemcoin1 == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                systemcoin1 = utxo;
            }
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        UTXO mymultitemp1 = null;
        UTXO mysystemcoin1 = null;
        for (UTXO utxo : getBalance()) {
            if (mymultitemp1 == null && Arrays.equals(utxo.getTokenidBuf(), multitemp.getTokenidBuf())) {
                mymultitemp1 = utxo;
            }
            if (mysystemcoin1 == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                mysystemcoin1 = utxo;
            }
            log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
        }
        assertEquals(multitemp.getValue().getValue() - 10000, multitemp1.getValue().getValue());
        assertEquals(1000, systemcoin1.getValue().getValue());
        assertEquals(10000, mymultitemp1.getValue().getValue());
        assertEquals(mysystemcoin.getValue().getValue() - 1000, mysystemcoin1.getValue().getValue());
    }

    public void exchangeTokenComplete(Transaction tx) throws Exception {
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(tx);
        rollingBlock.solve();

        String res = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        log.debug(res);
    }

    public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("resp block, hex : " + Utils.HEX.encode(data));
        // get other tokenid from wallet
        UTXO utxo = null;
        List<UTXO> ulist = getBalance();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                utxo = u;

            }
        }
        log.debug(utxo.getValue().toString());
        // Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000",
        // utxo.getValue().getTokenid()));
        // log.debug(baseCoin);
        Address destination = outKey.toAddress(networkParameters);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));
        log.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        checkBalance(utxo.getValue(), walletAppKit1.wallet().walletKeys(null));
    }

    public void payTokenA(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("resp block, hex : " + Utils.HEX.encode(data));
        // get other tokenid from wallet
        UTXO utxo = null;
        List<UTXO> ulist = getBalance();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                if (u.isMultiSig()) {
                    utxo = u;
                }
            }
        }
        log.debug(utxo.getValue().toString());
        // Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000",
        // utxo.getValue().getTokenid()));
        // log.debug(baseCoin);
        Address destination = outKey.toAddress(networkParameters);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));
        log.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        checkBalance(utxo.getValue(), walletAppKit1.wallet().walletKeys(null));
    }

    @Test
    public void testSaveOVS() throws Exception {
        ECKey outKey = new ECKey();

        VOS vos = new VOS();
        vos.setPubKey(outKey.getPublicKeyAsHex());
        vos.setNodeNumber(1);
        vos.setPrice(1);
        vos.setFrequence("");
        vos.setUrl("");
        vos.setContent("test");

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = this.networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_VOS);

        Transaction coinbase = new Transaction(this.networkParameters);
        coinbase.setDataClassName(DataClassName.VOS.name());
        coinbase.setData(vos.toByteArray());

        Sha256Hash sighash = coinbase.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(this.networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        coinbase.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(coinbase);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

        int blocktype = (int) Block.Type.BLOCKTYPE_VOS.ordinal();

        List<String> pubKeyList = new ArrayList<String>();
        pubKeyList.add(outKey.getPublicKeyAsHex());

        requestParam.clear();
        requestParam.put("blocktype", blocktype);
        requestParam.put("pubKeyList", pubKeyList);

        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.userDataList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        UserDataResponse userDataResponse = Json.jsonmapper().readValue(resp, UserDataResponse.class);
        List<String> dataList = userDataResponse.getDataList();

        assertEquals(dataList.size(), 1);

        String jsonStr = dataList.get(0);
        assertEquals(jsonStr, Utils.HEX.encode(vos.toByteArray()));
    }

    @Test
    public void testSaveOVSExecuteBatch() {
        for (int i = 0; i < 10; i++) {
            try {
                ECKey outKey = new ECKey();
                this.testSaveOVSExecute(outKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testSaveOVSExecuteBatch0() throws Exception {
        ECKey outKey = new ECKey();
        for (int i = 0; i < 10; i++) {
            try {
                this.testSaveOVSExecute(outKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("vosKey", outKey.getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getVOSExecuteList.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        VOSExecuteListResponse vosExecuteListResponse = Json.jsonmapper().readValue(resp, VOSExecuteListResponse.class);

        List<VOSExecute> vosExecutes = vosExecuteListResponse.getVosExecutes();
        assertTrue(vosExecutes.size() == 1);

        VOSExecute vosExecute = vosExecutes.get(0);
        assertTrue((int) vosExecute.getExecute() == 10);
    }

    public void testSaveOVSExecute(ECKey outKey) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = this.networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_VOS_EXECUTE);

        Transaction coinbase = new Transaction(this.networkParameters);
        VOSExecute vosExecute = new VOSExecute();
        vosExecute.setVosKey(outKey.getPublicKeyAsHex());
        vosExecute.setPubKey(outKey.getPublicKeyAsHex());
        vosExecute.setStartDate(new Date());
        vosExecute.setEndDate(new Date());
        vosExecute.setData(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        coinbase.setData(vosExecute.toByteArray());

        Sha256Hash sighash = coinbase.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(this.networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        coinbase.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(coinbase);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
    }

    @Test
    public void testSaveUserData() throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_USERDATA);
        ECKey outKey = new ECKey();

        Transaction transaction = new Transaction(networkParameters);
        Contact contact = new Contact();
        contact.setName("testname");
        contact.setAddress(outKey.toAddress(networkParameters).toBase58());
        ContactInfo contactInfo0 = new ContactInfo();
        List<Contact> list = new ArrayList<Contact>();
        list.add(contact);
        contactInfo0.setContactList(list);

        transaction.setDataClassName(DataClassName.CONTACTINFO.name());
        transaction.setData(contactInfo0.toByteArray());

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        block.addTransaction(transaction);
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.getUserData.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        ContactInfo contactInfo1 = new ContactInfo().parse(buf);
        assertTrue(contactInfo1.getContactList().size() == 1);

        Contact contact0 = contactInfo1.getContactList().get(0);
        assertTrue("testname".equals(contact0.getName()));

        transaction = new Transaction(networkParameters);
        contactInfo1.setContactList(new ArrayList<Contact>());
        transaction.setDataClassName(DataClassName.CONTACTINFO.name());
        transaction.setData(contactInfo1.toByteArray());

        sighash = transaction.getHash();
        party1Signature = outKey.sign(sighash);
        buf1 = party1Signature.encodeToDER();

        multiSignBies = new ArrayList<MultiSignBy>();
        multiSignBy0 = new MultiSignBy();
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        requestParam.clear();
        data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(), Json.jsonmapper().writeValueAsString(requestParam));
        block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_USERDATA);

        block.addTransaction(transaction);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        buf = OkHttp3Util.post(contextRoot + ReqCmd.getUserData.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        contactInfo1 = new ContactInfo().parse(buf);
        assertTrue(contactInfo1.getContactList().size() == 0);
    }

    @Test
    public void createTransaction() throws Exception {

        wallet1();
        wallet2();

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("resp block, hex : " + Utils.HEX.encode(data));

        Address destination = Address.fromBase58(networkParameters, "1NWN57peHapmeNq1ndDeJnjwPmC56Z6x8j");

        Coin amount = Coin.parseCoin("0.02", NetworkParameters.BIGTANGLE_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        request.tx.setMemo("memo");
        walletAppKit.wallet().completeTx(request, null);
        // request.tx.setDataclassname(DataClassName.USERDATA.name());

        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        log.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        getBalance();

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer()
                .makeTransaction(request.tx.bitcoinSerialize());
        log.info("transaction, memo : " + transaction.getMemo());
        // log.info("transaction, tokens : " +
        // Json.jsonmapper().writeValueAsString(transaction.getTokenInfo()));
        log.info("transaction, datatype : " + transaction.getDataClassName());
    }

    @Test
    public void clientMining() throws Exception {
        for (int i = 0; i <= 100000; i++) {
            clientMiningDo();

        }
    }

    public void clientMiningDo() throws Exception {

        Address destination = Address.fromBase58(networkParameters, "1NWN57peHapmeNq1ndDeJnjwPmC56Z6x8j");

        Coin amount = Coin.parseCoin("0.02", NetworkParameters.BIGTANGLE_TOKENID);
        walletAppKit.wallet().setAllowClientMining(true);
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        Collections.shuffle(keys);
        walletAppKit.wallet().setClientMiningAddress(keys.get(0).getPubKeyHash());
        walletAppKit.wallet().pay(null, destination, amount, "Client Mining");

    }

}
