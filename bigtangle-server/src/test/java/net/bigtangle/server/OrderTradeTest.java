package net.bigtangle.server;

import static org.junit.Assert.fail;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.server.service.OrderTickerService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderTradeTest extends AbstractIntegrationTest {

    @Autowired
    OrderTickerService tickerService;

    @Test
    public void payToWalletECKey() throws Exception {
        File f3 = new File("./logs/", "bigtangle4.wallet");
        if (f3.exists()) {
            f3.delete();
        }
        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        walletAppKit2.wallet().importKey(new ECKey());
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);

        for (ECKey ecKey : wallet2Keys) {
            System.out.println("pubKey : " + ecKey.getPublicKeyAsHex() + ", privKey : " + ecKey.getPrivateKeyAsHex());
        }

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amountToken = 88l;
        // split token
        payTestToken(testKey, amountToken);
        payTestToken(testKey, amountToken);
        checkBalanceSum(Coin.valueOf(2 * amountToken, testKey.getPubKey()), wallet2Keys);

    }

    @Test
    public void payBigToWalletECKey() throws Exception {
        File f3 = new File("./logs/", "bigtangle3.wallet");
        if (f3.exists()) {
            f3.delete();
        }
        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle3");
        walletAppKit1.wallet().setServerURL(contextRoot);
        walletAppKit1.wallet().importKey(new ECKey());
        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);

        for (ECKey ecKey : wallet1Keys) {
            System.out.println("pubKey : " + ecKey.getPublicKeyAsHex() + ", privKey : " + ecKey.getPrivateKeyAsHex());
        }

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amount = 77l;
        // split BIG
        payBig(amount);
        payBig(amount);
        checkBalanceSum(Coin.valueOf(2 * amount, NetworkParameters.BIGTANGLE_TOKENID), wallet1Keys);
    }

    @Test
    // test buy order with multiple inputs
    public void testBuy() throws Exception {

        File f3 = new File("./logs/", "bigtangle3.wallet");
        if (f3.exists()) {
            f3.delete();
        }

        File f4 = new File("./logs/", "bigtangle4.wallet");
        if (f4.exists()) {
            f4.delete();
        }

        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle3");
        walletAppKit1.wallet().setServerURL(contextRoot);
        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);

        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amountToken = 88l;
        // split token
        payTestToken(testKey, amountToken);
        payTestToken(testKey, amountToken);
        checkBalanceSum(Coin.valueOf(2 * amountToken, testKey.getPubKey()), wallet2Keys);

        long tradeAmount = 100l;
        long price = 1;
        Block block = walletAppKit2.wallet().sellOrder(null, testTokenId, price, tradeAmount, null, null,  
                NetworkParameters.BIGTANGLE_TOKENID_STRING,true);
        addedBlocks.add(block);
      //  blockGraph.confirm(block.getHash(), new HashSet<>(), (long) -1, store); // mcmcServiceUpdate();

        
        long amount = 77l;
        // split BIG
        payBig(amount);
     //   Thread.sleep(2000);
     //   mcmcServiceUpdate();
        payBig(amount);
        checkBalanceSum(Coin.valueOf(2 * amount, NetworkParameters.BIGTANGLE_TOKENID), wallet1Keys);
        // Open buy order for test tokens
        block = walletAppKit1.wallet().buyOrder(null, testTokenId, price, tradeAmount, null, null,NetworkParameters.BIGTANGLE_TOKENID_STRING,true);
        addedBlocks.add(block);

        // Execute order matching
        makeRewardBlock(addedBlocks);
        showOrders();

        // Verify the tokens changed position
        checkBalanceSum(Coin.valueOf(tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID), wallet2Keys);

        checkBalanceSum(Coin.valueOf(2 * amountToken - tradeAmount, testKey.getPubKey()), wallet2Keys);

        checkBalanceSum(Coin.valueOf(tradeAmount, testKey.getPubKey()), wallet1Keys);
        checkBalanceSum(Coin.valueOf(2 * amount - tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID),
                wallet1Keys);
    }

    @Test
    // test buy order with multiple inputs
    public void testOrderLargeThanLONGMAX() throws Exception {

        File f3 = new File("./logs/", "bigtangle3.wallet");
        if (f3.exists()) {
            f3.delete();
        }

        File f4 = new File("./logs/", "bigtangle4.wallet");
        if (f4.exists()) {
            f4.delete();
        }

        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle3");
        walletAppKit1.wallet().setServerURL(contextRoot);
        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);

        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amountToken = 88l;
        // split token
        payTestToken(testKey, amountToken);
        payTestToken(testKey, amountToken);
        checkBalanceSum(Coin.valueOf(2 * amountToken, testKey.getPubKey()), wallet2Keys);

        long tradeAmount = 10l;
        long price = Long.MAX_VALUE;
        try {
            Block block = walletAppKit2.wallet().sellOrder(null, testTokenId, price, tradeAmount, null, null,  
                    NetworkParameters.BIGTANGLE_TOKENID_STRING,true);
            fail();
        } catch (VerificationException e) {
            // TODO: handle exception
        }

    }

    @Test
    public void testBuySellWithDecimal() throws Exception {
        testBuySellWithDecimalDo(100000l, 70000000, 9);
    }

    @Test
    public void testBuySellWithDecimal1() throws Exception {
        testBuySellWithDecimalDo(100, 777000000l, 2);
    }

    public void testBuySellWithDecimalDo(long price, long tradeAmount, int tokendecimal) throws Exception {
        File f3 = new File("./logs/", "bigtangle3.wallet");
        if (f3.exists()) {
            f3.delete();
        }
        File f4 = new File("./logs/", "bigtangle4.wallet");
        if (f4.exists()) {
            f4.delete();
        }

        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle3");
        walletAppKit1.wallet().setServerURL(contextRoot);
        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);

        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token 
     
        makeTestToken(testKey, BigInteger.valueOf(tradeAmount * 1000), addedBlocks, tokendecimal);
        String testTokenId = testKey.getPublicKeyAsHex();

        payTestToken(testKey, tradeAmount * 2);
        checkBalanceSum(Coin.valueOf(tradeAmount * 2, testKey.getPubKey()), wallet2Keys);
        Block block = walletAppKit2.wallet().sellOrder(null, testTokenId, price, tradeAmount, null, null,  
                NetworkParameters.BIGTANGLE_TOKENID_STRING,true);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);

        long amount = 7700000000000l;
     
        payBig(amount);
        checkBalanceSum(Coin.valueOf(amount, NetworkParameters.BIGTANGLE_TOKENID), wallet1Keys);
        // Open buy order for test tokens
        block = walletAppKit1.wallet().buyOrder(null, testTokenId, price, tradeAmount, null, null,NetworkParameters.BIGTANGLE_TOKENID_STRING,true);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);

  
        
        // blockGraph.confirm(block.getHash(), new HashSet<>(), (long)
        // -1,store);
        // Execute order matching
        makeRewardBlock(addedBlocks);
        // showOrders();
        // Verify the tokens changed position
        checkBalanceSum(new Coin(walletAppKit1.wallet().totalAmount(tradeAmount, price,tokendecimal ,false),
                NetworkParameters.BIGTANGLE_TOKENID), wallet2Keys);

        checkBalanceSum(Coin.valueOf(tradeAmount, testKey.getPubKey()), wallet2Keys);

        checkBalanceSum(Coin.valueOf(tradeAmount, testKey.getPubKey()), wallet1Keys);

    }

}
