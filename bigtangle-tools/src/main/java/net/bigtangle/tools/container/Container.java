package net.bigtangle.tools.container;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import net.bigtangle.tools.account.Account;

public class Container extends ArrayList<Account> {

    private static final long serialVersionUID = -2908678813397748468L;

    public void initialize() {
        for (int i = 1; i <= 3; i++) {
            try {
                Account account = new Account("wallet" + String.valueOf(i));
                this.add(account);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static final Container instance = new Container();

    public static Container getInstance() {
        return instance;
    }

    public void run() {
        this.initialize();
        for (Iterator<Account> iterator = this.iterator(); iterator.hasNext();) {
            Account account = iterator.next();
            account.startTrade();
        }
        TokenPost tokenPost = TokenPost.getInstance();
        try {
            tokenPost.initialize();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Account randomTradeAccount() {
        if (this.size() == 0)
            return null;
        
        Random random = new Random();
        int index = random.nextInt(this.size());
        return this.get(index);
    }
}
