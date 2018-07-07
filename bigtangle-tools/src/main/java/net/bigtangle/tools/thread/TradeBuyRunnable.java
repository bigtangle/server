package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.impl.BuyOrderAction;

public class TradeBuyRunnable implements Runnable {

    private final Account account;
    
    public TradeBuyRunnable(final Account account) {
        this.account = account;
        this.account.initBuyOrderTask();
    }

    @Override
    public void run() {
        while (true) {
            try {
                BuyOrderAction  act = new BuyOrderAction(this.account);
                act.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
            }
        }
    }
}
