package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;

public class TradeRun implements Runnable {

    private final Account account;
    
    public TradeRun(final Account account) {
        this.account = account;
    }

    @Override
    public void run() {
        while (true) {
            try {
                this.account.doAction();
//                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
            }
        }
    }
}
