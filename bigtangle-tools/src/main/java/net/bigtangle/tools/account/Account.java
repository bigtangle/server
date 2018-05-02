package net.bigtangle.tools.account;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.action.impl.BalancesAction;
import net.bigtangle.tools.action.impl.PayAction;
import net.bigtangle.tools.config.Tools;
import net.bigtangle.tools.thread.TradeRun;

import org.spongycastle.crypto.params.KeyParameter;

public class Account {

    private HashMap<String, Action> executes = new HashMap<String, Action>();

    private final static NetworkParameters PARAMS = UnitTestParams.get();

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
        walletAppKit = new WalletAppKit(PARAMS, new File("."), walletPath);
        walletAppKit.wallet().setServerURL(Tools.CONTEXT_ROOT);

        Action action = new PayAction(this);
        action.execute();
        this.executes.put(BalancesAction.class.getSimpleName(), new BalancesAction(this));
    }

    public void doAction() {
    }

    public void startTrade() {
        Thread thread = new Thread(new TradeRun(this));
        thread.start();
    }

    public String getName() {
        return "account_" + walletPath;
    }

    public Random0 getRandom0() {
        return null;
    }
}
