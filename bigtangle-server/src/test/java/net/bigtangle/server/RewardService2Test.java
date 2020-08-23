/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Tokensums;
import net.bigtangle.core.TokensumsMap;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.service.CheckpointService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewardService2Test extends AbstractIntegrationTest {

    @Autowired
    CheckpointService checkpointService;

    public Block createReward(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {
        for (int j = 1; j < 2; j++) {
            payMoneyToWallet1(j, blocksAddedAll);
            mcmcServiceUpdate();
            
            sell(blocksAddedAll);
            buy(blocksAddedAll);
        }

        // Generate mining reward block
        Block next =  createReward(rewardBlock1.getHash(),store);
        blocksAddedAll.add(next);

        return next;
    }

    @Test
    // the switch to longest chain
    public void testReorgMiningReward() throws Exception {
        List<Block> a1 = new ArrayList<Block>();
        List<Block> a2 = new ArrayList<Block>();
        // first chains
        testToken(a1);
        mcmcServiceUpdate();
        Block r1 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            r1 = createReward(r1, a1);
        }
        log.debug(r1.toString());
        checkSum();
        store.resetStore();
        testToken(a2);
        // second chain
        Block r2 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 10; i++) {
            r2 = createReward(r2, a2);
        }
        checkSum();
        log.debug(r2.toString());
        assertTrue(r2.getRewardInfo().getChainlength() == store.getMaxConfirmedReward().getChainLength());

        Sha256Hash hash = checkpointService.checkToken(store).hash();
        // replay
        store.resetStore();

        // replay first chain
        for (Block b : a1) {
            if (b != null)
                blockGraph.add(b, true,true,store);
        }
        // check
        assertTrue(r1.getRewardInfo().getChainlength() == store.getMaxConfirmedReward().getChainLength());
        // replay second chain
        for (Block b : a2) {
            if (b != null)
                blockGraph.add(b, true,true,store);

        }
        assertTrue(r2.getRewardInfo().getChainlength() == store.getMaxConfirmedReward().getChainLength());

        Sha256Hash hash1=    checkSum();
        assertTrue( hash.equals(checkpointService.checkToken(store).hash()));
        // replay second and then replay first
        store.resetStore();
        for (Block b : a2) {
            if (b != null)
                blockGraph.add(b, true,true,store);

        }
        for (Block b : a1) {
            if (b != null)
                blockGraph.add(b, true,true,store);
        }
        assertTrue(r2.getRewardInfo().getChainlength() == store.getMaxConfirmedReward().getChainLength());
        assertTrue( hash.equals(checkpointService.checkToken(store).hash()));
        Sha256Hash hash2=  checkSum();
        assertTrue(hash1.equals(hash2));
    }

    private   Sha256Hash checkSum() throws JsonProcessingException, Exception {
        TokensumsMap map = checkpointService.checkToken(store);
        Map<String, Tokensums> r11 = map.getTokensumsMap();
        for (Entry<String, Tokensums> a : r11.entrySet()) {
            assertTrue(" " + a.toString(), a.getValue().check());
        }
        return map.hash();
    }

    public void testToken(List<Block> blocksAddedAll) throws Exception {

        blocksAddedAll.add(testCreateToken(walletAppKit.wallet().walletKeys().get(0), "test"));
        mcmcServiceUpdate();
        
        // testCreateToken(walletAppKit.wallet().walletKeys().get(1));
        // mcmcServiceUpdate();
        // testCreateToken(walletAppKit.wallet().walletKeys().get(2));
        // mcmcServiceUpdate();
        // testCreateToken(walletAppKit.wallet().walletKeys().get(3));
        // mcmcServiceUpdate();
        // sendEmpty(20);
    }

    public void sell(List<Block> blocksAddedAll) throws Exception {

        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : walletAppKit.wallet().walletKeys()) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        List<UTXO> utxos = getBalancesResponse.getOutputs();
        Collections.shuffle(utxos);
        long q = 8;
        for (UTXO utxo : utxos) {
            if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())) {
                walletAppKit.wallet().setServerURL(contextRoot);
                blocksAddedAll.add(walletAppKit.wallet().sellOrder(null, utxo.getTokenId(), 10000000, utxo.getValue().getValue().longValue(), null, null));

            }
        }
    }

    public void payMoneyToWallet1(int j, List<Block> blocksAddedAll) throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    3333000000L / LongMath.pow(2, j));
        }
        walletAppKit1.wallet().importKey(fromkey);
        mcmcServiceUpdate();
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payMoneyToWallet1");
        mcmcServiceUpdate();
        blocksAddedAll.add(b);
    }

    public void buy(List<Block> blocksAddedAll) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
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
                    orderRecord.getOfferValue(), null, null));
            mcmcServiceUpdate();
            
        }

    }

}