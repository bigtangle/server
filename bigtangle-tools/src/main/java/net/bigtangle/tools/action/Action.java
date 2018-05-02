package net.bigtangle.tools.action;

import net.bigtangle.tools.account.Account;

public abstract class Action {

    public void execute() {
        try {
            this.execute0();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            this.callback();
        }
    }
    
    public abstract void callback();
    
    public abstract void execute0() throws Exception;
    
    protected Account account;
    
    public Action(Account account) {
        this.account = account;
    }
}
