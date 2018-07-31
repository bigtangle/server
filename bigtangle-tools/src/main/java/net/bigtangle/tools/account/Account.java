package net.bigtangle.tools.account;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.action.impl.BuyOrderAction;
import net.bigtangle.tools.action.impl.MultiTokenAction;
import net.bigtangle.tools.action.impl.SellOrderAction;
import net.bigtangle.tools.action.impl.SignOrderAction;
import net.bigtangle.tools.action.impl.SingleTokenAction;
import net.bigtangle.tools.action.impl.TokenAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.thread.TokenRunnable;
import net.bigtangle.tools.thread.TradeBuyRunnable;
import net.bigtangle.tools.thread.TradeRunnable;
import net.bigtangle.tools.thread.TradeSellRunnable;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

public class Account {

    public void executePool(Runnable runnable) {
        this.executorService.submit(runnable);
    }

    private ExecutorService executorService = Executors.newFixedThreadPool(10);

    private List<SimpleAction> executes = new ArrayList<SimpleAction>();

    private List<ECKey> walletKeys = new ArrayList<ECKey>();

    public TokenCoinbase tokenCoinbase;

    private int index = 0;

    public Coin defaultCoinAmount() {
        return this.tokenCoinbase.getCoinDefaultValue();
    }

    public List<ECKey> walletKeys() throws Exception {
        return walletKeys;
    }

    public Account(String walletPath) {
        this.walletPath = walletPath;
        initWallet(walletPath);
    }

    private void initWallet(String walletPath) {
        walletAppKit = new WalletAppKit(Configure.PARAMS, new File("."), walletPath);
        KeyParameter aesKey = null;
        try {
            this.walletKeys = walletAppKit.wallet().walletKeys(aesKey);
        } catch (Exception e) {
        }
        walletAppKit.wallet().setServerURL(Configure.SIMPLE_SERVER_CONTEXT_ROOT);
    }

    private String walletPath;

    private WalletAppKit walletAppKit;

    public void initBuyOrderTask() {
        this.executes.add(new BuyOrderAction(this));
        this.executes.add(new SignOrderAction(this));
    }

    public void initSellOrderTask() {
        try {
            SimpleAction action2 = new TokenAction(this);
            action2.execute();
        } catch (Exception e) {
        }
        this.executes.add(new SignOrderAction(this));
        this.executes.add(new SellOrderAction(this));
    }

    public void initTradeOrderTask() {
        try {
            SimpleAction action2 = new TokenAction(this);
            action2.execute();
        } catch (Exception e) {
        }
        this.executes.add(new SignOrderAction(this));
        this.executes.add(new SellOrderAction(this));
    }

    public void initTokenTask() {
        this.executes.add(new MultiTokenAction(this));
        this.executes.add(new SingleTokenAction(this));
    }

    public void doAction() {
        int len = this.executes.size();
        SimpleAction action = this.executes.get(index);
        action.execute();
        index++;
        if (index == len) {
            index = 0;
        }
    }

    public String getName() {
        return "account_" + walletPath;
    }

    public ECKey getRandomTradeECKey() {
        int count = this.walletKeys.size();
        int index = new Random().nextInt(count);
        ECKey outKey = this.walletKeys.get(index);
        return outKey;
    }

    public ECKey getBuyKey() throws Exception {
        if (this.buyKey != null) {
            return this.buyKey;
        }
        for (ECKey ecKey : walletKeys()) {
            try {
                Set<String> pubKeyHashs = new HashSet<String>();
                pubKeyHashs.add(Utils.HEX.encode(ecKey.toAddress(Configure.PARAMS).getHash160()));

                String resp = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getBalances.name(),
                        Json.jsonmapper().writeValueAsString(pubKeyHashs));
                GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(resp, GetBalancesResponse.class);

                for (Coin coinbase : getBalancesResponse.getTokens()) {
                    if (Arrays.equals(coinbase.getTokenid(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                        this.buyKey = ecKey;
                        break;
                    }
                }
            } catch (Exception e) {
            }
        }
        return this.buyKey;
    }

    public ECKey getPushKey() throws Exception {
        if (this.pushKey != null) {
            return this.pushKey;
        }
        this.pushKey = this.walletKeys().get(0);
        return this.pushKey;
    }

    public List<ECKey> getSignKey() throws Exception {
        if (this.signKey != null && !this.signKey.isEmpty()) {
            return this.signKey;
        }
        this.signKey = new ArrayList<ECKey>();
        for (ECKey ecKey : this.walletKeys()) {
            signKey.add(ecKey);
            if (signKey.size() == 3) {
                break;
            }
        }
        return this.signKey;
    }

    private ECKey buyKey;

    private ECKey pushKey;

    private List<ECKey> signKey;

    public void completeTransaction(SendRequest request) throws Exception {
        this.walletAppKit.wallet().completeTx(request);
    }

    public void signTransaction(SendRequest request) throws Exception {
        this.walletAppKit.wallet().signTransaction(request);
    }

    public Wallet wallet() {
        return this.walletAppKit.wallet();
    }

    public void startBuyOrder() {
        Thread thread = new Thread(new TradeBuyRunnable(this));
        thread.start();
    }

    public void startSellOrder() {
        Thread thread = new Thread(new TradeSellRunnable(this));
        thread.start();
    }

    public void startTradeOrder() {
        Thread thread = new Thread(new TradeRunnable(this));
        thread.start();
    }

    public void startToken() {
        Thread thread = new Thread(new TokenRunnable(this));
        thread.start();
    }

    public void syncTokenCoinbase(List<Coin> list) {
        this.tokenCoinbase.syncTokenCoinbase(list);
    }
}
