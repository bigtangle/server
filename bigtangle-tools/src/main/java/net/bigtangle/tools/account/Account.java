package net.bigtangle.tools.account;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.bigtangle.core.ECKey;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.action.impl.BalancesAction;
import net.bigtangle.tools.action.impl.BuyOrderAction;
import net.bigtangle.tools.action.impl.ExchangeAction;
import net.bigtangle.tools.action.impl.PayAction;
import net.bigtangle.tools.action.impl.SellOrderAction;
import net.bigtangle.tools.action.impl.SignOrderAction;
import net.bigtangle.tools.action.impl.TransferAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.thread.TradeRun;
import net.bigtangle.wallet.SendRequest;

import org.spongycastle.crypto.params.KeyParameter;

public class Account {

    private List<Action> executes = new ArrayList<Action>();

    private Random random = new Random();

    public List<ECKey> walletKeys() throws Exception {
        List<ECKey> walletKeys = walletAppKit.wallet().walletKeys(aesKey);
        return walletKeys;
    }

    private KeyParameter aesKey = null;

    public Account(String walletPath) {
        this.walletPath = walletPath;
        this.initialize();
    }

    private String walletPath;

    public WalletAppKit walletAppKit;

    public void initialize() {
        // init wallet
        walletAppKit = new WalletAppKit(Configure.PARAMS, new File("."), walletPath);
        walletAppKit.wallet().setServerURL(Configure.CONTEXT_ROOT);

        // give amount
        Action action = new PayAction(this);
        action.execute();

        // init action
        this.executes.add(new BalancesAction(this));
        this.executes.add(new BuyOrderAction(this));
        this.executes.add(new SellOrderAction(this));
        this.executes.add(new TransferAction(this));
        this.executes.add(new SignOrderAction(this));
        this.executes.add(new ExchangeAction(this));
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
        return new RandomTrade("", "");
    }

    public ECKey getRandomECKey() {
        List<ECKey> walletKeys = null;
        try {
            walletKeys = this.walletKeys();
        } catch (Exception e) {
        }
        if (walletKeys == null || walletKeys.isEmpty()) {
            return null;
        }
        int index = random.nextInt(walletKeys.size());
        return walletKeys.get(index);
    }

    public void completeTransaction(SendRequest request) throws Exception {
        this.walletAppKit.wallet().completeTx(request);
    }
    
    public void signTransaction(SendRequest request) throws Exception {
        this.walletAppKit.wallet().signTransaction(request);
    }
}
