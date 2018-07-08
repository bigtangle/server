package net.bigtangle.tools.account;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.ECKey;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.action.impl.BuyOrderAction;
import net.bigtangle.tools.action.impl.SellOrderAction;
import net.bigtangle.tools.action.impl.SignOrderAction;
import net.bigtangle.tools.action.impl.TokenAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.thread.TradeBuyRunnable;
import net.bigtangle.tools.thread.TradeSellRunnable;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

public class Account {

    private List<Action> executes = new ArrayList<Action>();

    private Random random = new Random();

    private List<ECKey> walletKeys = new ArrayList<ECKey>();

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

    public boolean calculatedAddressHit(String address) {
        try {
            for (ECKey key : this.walletKeys()) {
                String n = key.toAddress(Configure.PARAMS).toString();
                if (n.equalsIgnoreCase(address)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private String walletPath;

    private WalletAppKit walletAppKit;

    public void initBuyOrderTask() {
        this.executes.add(new BuyOrderAction(this));
        this.executes.add(new SignOrderAction(this));
    }
    
    public void initSellOrderTask() {
        try {
            Action action2 = new TokenAction(this);
            action2.execute();
        } catch (Exception e) {
        }
        this.executes.add(new SellOrderAction(this));
        this.executes.add(new SignOrderAction(this));
    }
    
    public void doAction() {
        if (this.executes == null || this.executes.isEmpty()) {
            return;
        }
        int index = random.nextInt(this.executes.size());
        Action action = this.executes.get(index);
        action.execute();
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

    public ECKey getBuyKey() {
        List<ECKey> walletKeys = null;
        try {
            walletKeys = this.walletKeys();
        } catch (Exception e) {
        }
        if (walletKeys == null || walletKeys.isEmpty()) {
            return null;
        }
        ECKey outKey = walletKeys.get(1);
        return outKey;
    }

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
}
