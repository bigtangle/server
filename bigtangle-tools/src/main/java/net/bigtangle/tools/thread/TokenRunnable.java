package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;

public class TokenRunnable implements Runnable {
    
    private final Account account;
    
    public TokenRunnable(final Account account) {
        this.account = account;
        this.account.initTokenTask();
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
