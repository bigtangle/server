package net.bigtangle.tools.account;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.action.impl.BuyOrderAction;
import net.bigtangle.tools.action.impl.PayAction;
import net.bigtangle.tools.action.impl.SellOrderAction;
import net.bigtangle.tools.action.impl.SignOrderAction;
import net.bigtangle.tools.action.impl.TokenAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.thread.TradeRun;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

public class Account {

    private List<Action> executes = new ArrayList<Action>();

    private Random random = new Random();

    // private List<String> tokenHexList = new ArrayList<String>();
    
//    private ThreadLocal<ECKey> threadLocal = new ThreadLocal<ECKey>();
    
    private List<ECKey> walletKeys = new ArrayList<ECKey>();

    public List<ECKey> walletKeys() throws Exception {
        return walletKeys;
    }

    private KeyParameter aesKey = null;

    public Account(String walletPath) {
        this.walletPath = walletPath;
        this.initialize();
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

    // @SuppressWarnings("unchecked")
    public void initialize() {
        // init wallet
        walletAppKit = new WalletAppKit(Configure.PARAMS, new File("."), walletPath);
        KeyParameter aesKey = null;
        try {
            this.walletKeys = walletAppKit.wallet().walletKeys(aesKey);
        } catch (Exception e) {
        }
        walletAppKit.wallet().setServerURL(Configure.CONTEXT_ROOT);
        try {
            // gen token
            Action action2 = new TokenAction(this);
            action2.execute();
        } catch (Exception e) {
        }
        try {
            // give amount
            Action action1 = new PayAction(this);
            action1.execute();
        } catch (Exception e) {
        }
        // init action
//        this.executes.add(new BalancesAction(this));
        this.executes.add(new BuyOrderAction(this));
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

    public void startTrade() {
        Thread thread = new Thread(new TradeRun(this));
        thread.start();
    }

    public String getName() {
        return "account_" + walletPath;
    }

    public RandomTrade getRandomTrade() {
        ECKey outKey = this.getSellKey();
        String address = outKey.toAddress(Configure.PARAMS).toBase58();
        return new RandomTrade(address, Utils.HEX.encode(outKey.getPubKeyHash()));
    }

    public ECKey getSellKey() {
        List<ECKey> walletKeys = null;
        try {
            walletKeys = this.walletKeys();
        } catch (Exception e) {
        }
        if (walletKeys == null || walletKeys.isEmpty()) {
            return null;
        }
        ECKey outKey = walletKeys.get(0);
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
}
