package net.bigtangle.tools.account;

import java.util.HashMap;

import net.bigtangle.tools.account.action.Action;
import net.bigtangle.tools.account.action.impl.PayAction;
import net.bigtangle.tools.account.thread.TradeRun;

public class Account {

    private HashMap<String, Action> executes = new HashMap<String, Action>();
    
    public Account() {
    }
    
    public void initialize() {
        this.executes.put(PayAction.class.getSimpleName(), new PayAction(this));
    }
    
    public void doAction() {
        
    }
    
    public void startTrade() {
        Thread thread = new Thread(new TradeRun(this));
        thread.start();
    }
}
