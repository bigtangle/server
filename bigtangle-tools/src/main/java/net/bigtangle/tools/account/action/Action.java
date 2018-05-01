package net.bigtangle.tools.account.action;

import net.bigtangle.tools.account.Account;

public abstract class Action {

    public void execute() {
        try {
            this.execute0();
        }
        finally {
            this.callback();
        }
    }
    
    public abstract void callback();
    
    public abstract void execute0();
    
    protected Account account;
    
    public Action(Account account) {
        this.account = account;
    }
}
