package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;

public class TradeSellRunnable implements Runnable {

    private final Account account;
    
    public TradeSellRunnable(final Account account) {
        this.account = account;
        this.account.initSellOrderTask();
    }

    @Override
    public void run() {
        while (true) {
            try {
                this.account.doAction();
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
            }
        }
    }
}
