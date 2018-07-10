package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;

public class TradeRunnable implements Runnable {

    private final Account account;
    
    public TradeRunnable(final Account account) {
        this.account = account;
        this.account.initTradeOrderTask();
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
