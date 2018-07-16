package net.bigtangle.tools.account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;

public class TokenCoinbase {
    
    public void syncTokenCoinbase(List<Coin> list) {
        if (list.isEmpty()) return;
        lock.lock();
        try {
            Map<String, Coin> values = new HashMap<String, Coin>();
            for (Coin coin : list) {
                String tokenHex = coin.getTokenHex();
                values.put(tokenHex, coin);
            }
            this.values = values;
        }
        finally {
            lock.unlock();
        }
    }
    
    private Lock lock = new ReentrantLock();
    
    public TokenCoinbase(Account account) {
    }
    
    private Map<String, Coin> values = new HashMap<String, Coin>();
    
    public Coin getCoinDefaultValue() {
        Coin coin = this.values.get(NetworkParameters.BIGNETCOIN_TOKENID_STRING);
        if (coin == null) {
            return Coin.ZERO;
        }
        return coin;
    }
}
