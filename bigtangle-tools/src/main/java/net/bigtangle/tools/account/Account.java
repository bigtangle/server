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
import net.bigtangle.tools.action.impl.PayAction;
import net.bigtangle.tools.action.impl.SellOrderAction;
import net.bigtangle.tools.action.impl.SignOrderAction;
import net.bigtangle.tools.action.impl.TokenAction;
import net.bigtangle.tools.action.impl.TransferAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.thread.TradeRun;
import net.bigtangle.wallet.SendRequest;

import org.spongycastle.crypto.params.KeyParameter;

public class Account {

    private List<Action> executes = new ArrayList<Action>();

    private Random random = new Random();

    // private List<String> tokenHexList = new ArrayList<String>();

    public List<ECKey> walletKeys() throws Exception {
        List<ECKey> walletKeys = walletAppKit.wallet().walletKeys(aesKey);
        return walletKeys;
    }

    private KeyParameter aesKey = null;

    public Account(String walletPath) {
        this.walletPath = walletPath;
        this.initialize();
    }

    public boolean calculatedAddressHit(String address) throws Exception {
        for (ECKey key : this.walletKeys()) {
            String n = key.toAddress(Configure.PARAMS).toString();
            if (n.equalsIgnoreCase(address)) {
                return true;
            }
        }
        return false;
    }

    private String walletPath;

    private WalletAppKit walletAppKit;

    // @SuppressWarnings("unchecked")
    public void initialize() {
        // init wallet
        walletAppKit = new WalletAppKit(Configure.PARAMS, new File("."), walletPath);
        walletAppKit.wallet().setServerURL(Configure.CONTEXT_ROOT);

        try {
            // give amount
            Action action1 = new PayAction(this);
            action1.execute();
        } catch (Exception e) {
        }
        try {
            // gen token
            Action action2 = new TokenAction(this);
            action2.execute();
        } catch (Exception e) {
        }

        // init action
        this.executes.add(new BalancesAction(this));
        this.executes.add(new BuyOrderAction(this));
        this.executes.add(new SellOrderAction(this));
        this.executes.add(new TransferAction(this));
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
        ECKey ecKey = this.getRandomECKey();
        String address = ecKey.toAddress(Configure.PARAMS).toBase58();
        return new RandomTrade(address, ecKey.getPublicKeyAsHex());
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
