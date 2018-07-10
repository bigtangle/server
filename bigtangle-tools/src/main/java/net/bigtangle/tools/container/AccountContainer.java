package net.bigtangle.tools.container;

import java.util.ArrayList;

import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.utils.GiveMoneyUtils;

public class AccountContainer extends ArrayList<Account> {

    private static final long serialVersionUID = -2908678813397748468L;

    public static AccountContainer newInstance() {
        AccountContainer container = new AccountContainer();
        return container;
    }

    public void startSellOrder(int startIndex, int endIndex) {
        for (int i = startIndex; i <= endIndex; i++) {
            try {
                Account account = new Account("wallet" + String.valueOf(i));
                account.startSellOrder();
                this.add(account);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startBuyOrder(int startIndex, int endIndex) {
        for (int i = startIndex; i <= endIndex; i++) {
            try {
                Account account = new Account("wallet" + String.valueOf(i));
                account.startBuyOrder();
                this.add(account);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startGiveMoney(int startIndex, int endIndex) throws Exception {
        for (int i = startIndex; i <= endIndex; i++) {
            try {
                Account account = new Account("wallet" + String.valueOf(i));
                GiveMoneyUtils.give(account.getBuyKey());
                this.add(account);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void startTradeOrder(int startIndex, int endIndex) {
        for (int i = startIndex; i <= endIndex; i++) {
            try {
                Account account = new Account("wallet" + String.valueOf(i));
                account.startTradeOrder();
                this.add(account);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
