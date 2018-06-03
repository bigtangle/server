/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

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

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("fromAddress", "fromAddress");
        requestParam.put("fromTokenHex", "fromTokenHex");
        requestParam.put("fromAmount", "22");
        requestParam.put("toAddress", "toAddress");
        requestParam.put("toTokenHex", "toTokenHex");
        requestParam.put("toAmount", "33");
        requestParam.put("orderid", UUID.randomUUID().toString());
        requestParam.put("dataHex", Utils.HEX.encode(a));
        String data = OkHttp3Util.post(contextRoot + ReqCmd.saveExchange.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        logger.info("testGetBalances resp : " + data);

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);

        // byte[] buf = BeanSerializeUtil.serializer(req.tx);
        // Transaction transaction = BeanSerializeUtil.deserialize(buf,
        // Transaction.class);

        SendRequest request = SendRequest.forTx(transaction);
        walletAppKit1.wallet().signTransaction(request);
        exchangeTokenComplete(request.tx);

        requestParam.clear();
        requestParam.put("address", "fromAddress");

        String response = OkHttp3Util.post(contextRoot + ReqCmd.getExchange.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        logger.info("getExchange resp : " + requestParam);
    }

    public void exchangeTokenComplete(Transaction tx) throws Exception {
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(tx);
        rollingBlock.solve();

        String res = OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        System.out.println(res);
    }

    public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
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
        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
        milestoneService.update();
    }

    @Test
    public void createTransaction() throws Exception {
        milestoneService.update();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));

        Address destination = Address.fromBase58(networkParameters, "mqrXsaFj9xV9tKAw7YeP1B6zPmfEP2kjfK");

        Coin amount = Coin.parseCoin("0.02", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        request.tx.setMemo("memo");
        walletAppKit.wallet().completeTx(request);
        request.tx.setDataType(10000);

        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        testTransactionAndGetBalances();

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer()
                .makeTransaction(request.tx.bitcoinSerialize());
        logger.info("transaction, memo : " + transaction.getMemo());
        // logger.info("transaction, tokens : " +
        // Json.jsonmapper().writeValueAsString(transaction.getTokenInfo()));
        logger.info("transaction, datatype : " + transaction.getDataType());
    }

}
