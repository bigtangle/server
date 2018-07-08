package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;

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
                account.doAction();
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }
    }
}
