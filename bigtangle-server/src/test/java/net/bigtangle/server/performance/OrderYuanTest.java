package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Tokensums;
import net.bigtangle.core.TokensumsMap;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.server.service.CheckpointService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderYuanTest extends AbstractIntegrationTest {

    @Autowired
    CheckpointService checkpointService;

    @Test
    public void payTokenTime() throws Exception {
        walletAppKit.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        walletAppKit.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

        long tokennumber = 100000000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token
        makeTestToken(testKey, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        Address address = walletAppKit.wallet().walletKeys().get(0).toAddress(networkParameters);
        Coin amount = MonetaryFormat.FIAT.noCode().parse("1", Utils.HEX.decode(yuanTokenPub), 2);
        long start = System.currentTimeMillis();
        walletAppKit.wallet().pay(null, address, amount, "");
        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    @Test
    public void buyBaseToken() throws Exception {
        walletAppKit.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        walletAppKit.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

        long tokennumber = 100000000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token
        makeTestToken(testKey, BigInteger.valueOf(tokennumber), addedBlocks, 2);

        while (true) {
            try {
                int num = Math.abs((new Random()).nextInt() % 10);
                for (int i = 0; i < num; i++) {
                    sell();
                    makeRewardBlock();
                }
                buy();
                // Execute order matching
                makeRewardBlock(addedBlocks);

                checkSum();
            } catch (Exception e) {
                // TODO: handle exception
                log.warn("", e);
                ;
            }
        }

    }

    private Sha256Hash checkSum() throws JsonProcessingException, Exception {
        TokensumsMap map = checkpointService.checkToken(store);
        Map<String, Tokensums> r11 = map.getTokensumsMap();
        for (Entry<String, Tokensums> a : r11.entrySet()) {
            if (!a.getValue().check()) {
                log.debug(a.toString());
                BigInteger.valueOf(123).divideAndRemainder(BigInteger.valueOf(LongMath.checkedPow(10, 2)));
            }
            assertTrue(" " + a.toString(), a.getValue().check());
        }
        return map.hash();
    }

    public void sell() throws Exception {
        List<UTXO> utxos = getBalance(false, walletKeys);
        Collections.shuffle(utxos);
        long q = Math.abs((new Random()).nextInt() % 10) + 1;
        for (UTXO utxo : utxos) {
            if (!yuanTokenPub.equals(utxo.getTokenId()) && utxo.getValue().getValue().signum() > 0
                    && !utxo.isSpendPending() && utxo.getValue().getValue().compareTo(BigInteger.valueOf(q)) >= 0) {
                try {
                    walletAppKit.wallet().sellOrder(null, utxo.getTokenId(),
                            100000000000l * (Math.abs((new Random()).nextInt() % 10) + 1), q, null, null, yuanTokenPub,
                            true);
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        }

    }

    public void buy() throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            try {
                buy(orderRecord);
                makeRewardBlock();
            } catch (Exception e) {
                // TODO: handle exception
            }
        }

    }

    public void buy(OrderRecord orderRecord) throws Exception {
        // sell order and make buy
        long price = orderRecord.getPrice();
        walletAppKit.wallet().buyOrder(null, orderRecord.getOfferTokenid(), price, orderRecord.getOfferValue(), null,
                null, orderRecord.getOrderBaseToken(), false);

    }
}
