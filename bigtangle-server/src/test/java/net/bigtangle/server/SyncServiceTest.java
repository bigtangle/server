/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.MissingNumberCheckService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SyncServiceTest extends AbstractIntegrationTest {

    @Autowired
    ServerConfiguration serverConfiguration;
    
    public void createNonChain(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {
        for (int j = 1; j < 2; j++) {
            payMoneyToWallet1(j, blocksAddedAll);
            makeRewardBlock(blocksAddedAll);

            sell(blocksAddedAll);
            buy(blocksAddedAll);
        }
    }

    public Block createReward(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {

        // Generate mining reward block
        Block next = makeRewardBlock(rewardBlock1.getHash());
        blocksAddedAll.add(next);

        return next;
    }

    @Test
    public void testSync() throws Exception {
        List<Block> a1 = new ArrayList<Block>();
        testToken(a1);
        makeRewardBlock(a1);
        Block r1 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            createNonChain(r1, a1);
        }
        serverConfiguration.setRequester(contextRoot);
        syncBlockService.startSingleProcess();
        for (int i = 0; i < 130; i++) {
        createReward(r1, a1);
        }
        syncBlockService.startSingleProcess();
    }

    @Test
    public void testSyncCheckChain() throws Exception {
        List<Block> a1 = new ArrayList<Block>();
        testToken(a1);
        makeRewardBlock(a1);
        Block r1 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            createNonChain(r1, a1);
        }
        serverConfiguration.setRequester(contextRoot);
        syncBlockService.startSingleProcess();
        for (int i = 0; i < 130; i++) {
        createReward(r1, a1);
        }
        List<TXReward> allConfirmedReward = store.getAllConfirmedReward();
        MissingNumberCheckService  missingNumberCheckService=new MissingNumberCheckService();
        missingNumberCheckService.check(allConfirmedReward);
    }

    
    public void testToken(List<Block> blocksAddedAll) throws Exception {

        blocksAddedAll.add(testCreateToken(walletAppKit.wallet().walletKeys().get(0), "test"));
        makeRewardBlock(blocksAddedAll);

    }

    public void sell(List<Block> blocksAddedAll) throws Exception {

        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : walletAppKit.wallet().walletKeys()) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        List<UTXO> utxos = getBalancesResponse.getOutputs();
        Collections.shuffle(utxos);
        long q = 8;
        for (UTXO utxo : utxos) {
            if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())) {
                walletAppKit.wallet().setServerURL(contextRoot);
                try {
                    blocksAddedAll.add(walletAppKit.wallet().sellOrder(null, utxo.getTokenId(), 10000000,
                            utxo.getValue().getValue().longValue(), null, null,
                            NetworkParameters.BIGTANGLE_TOKENID_STRING, true));
                } catch (InsufficientMoneyException e) {
                    // ignore: handle exception
                }
            }
        }
    }

    public void payMoneyToWallet1(int j, List<Block> blocksAddedAll) throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    3333000000L / LongMath.pow(2, j));
        }
        walletAppKit1.wallet().importKey(fromkey);
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payMoneyToWallet1");
        blocksAddedAll.add(b);
        makeRewardBlock(blocksAddedAll);
    }

    public void buy(List<Block> blocksAddedAll) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
       byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            try {
                buy(orderRecord, blocksAddedAll);
            } catch (InsufficientMoneyException e) {
                Thread.sleep(4000);
            } catch (Exception e) {
                log.debug("", e);
            }
        }
    }

    public void buy(OrderRecord orderRecord, List<Block> blocksAddedAll) throws Exception {

        if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
            // sell order and make buy
            long price = orderRecord.getTargetValue() / orderRecord.getOfferValue();
            walletAppKit.wallet().setServerURL(contextRoot);
            blocksAddedAll.add(walletAppKit.wallet().buyOrder(null, orderRecord.getOfferTokenid(), price,
                    orderRecord.getOfferValue(), null, null, NetworkParameters.BIGTANGLE_TOKENID_STRING, false));
            makeRewardBlock(blocksAddedAll);

        }

    }

}