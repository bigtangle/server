package net.bigtangle.tools.account.thread;

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
            }
            finally {
            }
        }
    }
}
