package net.bigtangle.tools.thread;

import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.impl.BuyOrderAction;

public class BuyRun implements Runnable {

    private final Account account;
    
    public BuyRun(final Account account) {
        this.account = account;
    }

    @Override
    public void run() {
        while (true) {
            try {
                BuyOrderAction  act = new BuyOrderAction(this.account);
                act.execute();
//                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
            }
        }
    }
}
