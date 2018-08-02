/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Contact;
import net.bigtangle.core.ContactInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOS;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.http.server.resp.UserDataResponse;
import net.bigtangle.core.http.server.resp.VOSExecuteListResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void testGiveMoney() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));

        HashMap<String, Integer> giveMoneyResult = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            ECKey outKey = new ECKey();
            giveMoneyResult.put(outKey.toAddress(networkParameters).toBase58(), 1000);
        }

        Coin coinbase = Coin.ZERO;
        Transaction doublespent = new Transaction(networkParameters);

        for (Map.Entry<String, Integer> entry : giveMoneyResult.entrySet()) {
            Coin amount = Coin.valueOf(entry.getValue() * 1000, NetworkParameters.BIGNETCOIN_TOKENID);
            Address address = Address.fromBase58(networkParameters, entry.getKey());
            doublespent.addOutput(amount, address);
            coinbase = coinbase.add(amount);
        }

        UTXO findOutput = null;
        for (UTXO output : testTransactionAndGetBalances(false, genesiskey)) {
            if (Arrays.equals(coinbase.getTokenid(), output.getValue().getTokenid())) {
                findOutput = output;
            }
        }

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, findOutput, 0);
        Coin amount = spendableOutput.getValue().subtract(coinbase);

        doublespent.addOutput(amount, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction,
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(doublespent);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        for (UTXO utxo : testTransactionAndGetBalances(false, genesiskey)) {
            logger.info("UTXO : " + utxo);
            if (Arrays.equals(coinbase.getTokenid(), utxo.getValue().getTokenid())) {
                assertTrue(utxo.getValue().value == 999999997000000L);
            }
        }
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testWalletImportKeyGiveMoney() throws Exception {
        Wallet coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);

        ECKey outKey = new ECKey();
        
        for (int i = 0; i < 3; i ++) {
            Transaction transaction = new Transaction(this.networkParameters);
            Coin amount = Coin.parseCoin("10000", NetworkParameters.BIGNETCOIN_TOKENID);
            transaction.addOutput(amount, outKey);
            
            SendRequest request = SendRequest.forTx(transaction);
            coinbaseWallet.completeTx(request);
            
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction, Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
            rollingBlock.addTransaction(request.tx);
            rollingBlock.solve();
    
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
            
            List<TransactionOutput> candidates = coinbaseWallet.calculateAllSpendCandidates(false);
            for (TransactionOutput transactionOutput : candidates) {
                logger.info("UTXO : " + transactionOutput);
            }
            
            for (UTXO output : this.testTransactionAndGetBalances(true, outKey)) {
                logger.info("UTXO : " + output);
            }
        }
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void testWalletBatchGiveMoney() throws Exception {
        Wallet coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);

        
        Transaction transaction = new Transaction(this.networkParameters);
        for (int i = 0; i < 3; i ++) {
        	ECKey outKey = new ECKey();
            Coin amount = Coin.parseCoin("3", NetworkParameters.BIGNETCOIN_TOKENID);
            transaction.addOutput(amount, outKey);
        }
        
        SendRequest request = SendRequest.forTx(transaction);
        coinbaseWallet.completeTx(request);
        
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction, Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        
        List<TransactionOutput> candidates = coinbaseWallet.calculateAllSpendCandidates(false);
        for (TransactionOutput transactionOutput : candidates) {
            logger.info("UTXO : " + transactionOutput);
        }
        /*
        for (UTXO output : this.testTransactionAndGetBalances(true, outKey)) {
            logger.info("UTXO : " + output);
        }*/
    }

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);
    @Autowired
    private MilestoneService milestoneService;

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

        logger.info("searchBlock resp : " + response);

    }

    @SuppressWarnings("deprecation")
    @Test
    public void exchangeToken() throws Exception {

        // get token from wallet to spent
        ECKey yourKey = walletAppKit1.wallet().walletKeys(null).get(0);
        System.err.println("toKey : " + yourKey.toAddress(networkParameters).toBase58());

        payToken(yourKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(yourKey);
        List<UTXO> utxos = testTransactionAndGetBalances(false, keys);
        UTXO yourutxo = utxos.get(0);
        List<UTXO> ulist = testTransactionAndGetBalances();
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                myutxo = u;
            }
        }
        System.err.println("outKey : " + myutxo.getAddress());

        Coin amount = Coin.valueOf(10000, yourutxo.getValue().tokenid);
        SendRequest req = SendRequest.to(new Address(networkParameters, myutxo.getAddress()), amount);
        req.tx.addOutput(myutxo.getValue(), new Address(networkParameters, yourutxo.getAddress()));

        System.out.println(myutxo.getAddress() + ", " + myutxo.getValue());
        System.out.println(yourutxo.getAddress() + ", " + amount);

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

        // String response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.getExchange.name(),
        // Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        // logger.info("getExchange resp : " + requestParam);
    }

    public void exchangeTokenComplete(Transaction tx) throws Exception {
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(tx);
        rollingBlock.solve();

        String res = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        System.out.println(res);
    }

    public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));
        // get other tokenid from wallet
        UTXO utxo = null;
        List<UTXO> ulist = testTransactionAndGetBalances();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                utxo = u;
            }
        }
        System.out.println(utxo.getValue());
        // Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000",
        // utxo.getValue().getTokenid()));
        // System.out.println(baseCoin);
        Address destination = outKey.toAddress(networkParameters);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

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
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = this.networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(NetworkParameters.BLOCKTYPE_VOS);

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
        milestoneService.update();
        
        int blocktype = (int) NetworkParameters.BLOCKTYPE_VOS;

        List<String> pubKeyList = new ArrayList<String>();
        pubKeyList.add(outKey.getPublicKeyAsHex());
        
        requestParam.clear();
        requestParam.put("blocktype", blocktype);
        requestParam.put("pubKeyList", pubKeyList);

        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.userDataList.name(), Json.jsonmapper().writeValueAsString(requestParam));
        
        UserDataResponse userDataResponse = Json.jsonmapper().readValue(resp, UserDataResponse.class);
        List<String> dataList = userDataResponse.getDataList();
        
        assertEquals(dataList.size(), 1);
        
        String jsonStr = dataList.get(0);
        assertEquals(jsonStr, Utils.HEX.encode(vos.toByteArray()));
    }
    
    @Test
    public void testSaveOVSExecuteBatch() {
        for (int i = 0; i < 10; i ++) {
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
        for (int i = 0; i < 10; i ++) {
            try {
                this.testSaveOVSExecute(outKey);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        milestoneService.update();
        
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("vosKey", outKey.getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getVOSExecuteList.name(), Json.jsonmapper().writeValueAsString(requestParam));
        
        VOSExecuteListResponse vosExecuteListResponse = Json.jsonmapper().readValue(resp, VOSExecuteListResponse.class);
        
        List<VOSExecute> vosExecutes = vosExecuteListResponse.getVosExecutes();
        assertTrue(vosExecutes.size() == 1);
        
        VOSExecute vosExecute = vosExecutes.get(0);
        assertTrue((int) vosExecute.getExecute() == 10);
    }
    
    public void testSaveOVSExecute(ECKey outKey) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = this.networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(NetworkParameters.BLOCKTYPE_VOS_EXECUTE);

        Transaction coinbase = new Transaction(this.networkParameters);
        VOSExecute vosExecute = new VOSExecute();
        vosExecute.setVosKey(outKey.getPublicKeyAsHex());
        vosExecute.setPubKey(outKey.getPublicKeyAsHex());
        vosExecute.setStartDate(new Date());
        vosExecute.setEndDate(new Date());
        vosExecute.setData(new byte[] {0x00, 0x00, 0x00, 0x00});
        
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
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(NetworkParameters.BLOCKTYPE_USERDATA);
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
        
        milestoneService.update();
        
        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.getUserData.name(), Json.jsonmapper().writeValueAsString(requestParam));

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
        data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(), Json.jsonmapper().writeValueAsString(requestParam));
        block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(NetworkParameters.BLOCKTYPE_USERDATA);
        
        block.addTransaction(transaction);
        block.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        milestoneService.update();
        
        requestParam.clear();
        requestParam.put("dataclassname", DataClassName.CONTACTINFO.name());
        requestParam.put("pubKey", Utils.HEX.encode(outKey.getPubKey()));
        buf = OkHttp3Util.post(contextRoot + ReqCmd.getUserData.name(), Json.jsonmapper().writeValueAsString(requestParam));
        
        contactInfo1 = new ContactInfo().parse(buf);
        assertTrue(contactInfo1.getContactList().size() == 0);
    }

    @Test
    public void createTransaction() throws Exception {
        milestoneService.update();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));

        Address destination = Address.fromBase58(networkParameters, "mqrXsaFj9xV9tKAw7YeP1B6zPmfEP2kjfK");

        Coin amount = Coin.parseCoin("0.02", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        request.tx.setMemo("memo");
        walletAppKit.wallet().completeTx(request);
        // request.tx.setDataclassname(DataClassName.USERDATA.name());

        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        testTransactionAndGetBalances();

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer()
                .makeTransaction(request.tx.bitcoinSerialize());
        logger.info("transaction, memo : " + transaction.getMemo());
        // logger.info("transaction, tokens : " +
        // Json.jsonmapper().writeValueAsString(transaction.getTokenInfo()));
        logger.info("transaction, datatype : " + transaction.getDataClassName());
    }

}
