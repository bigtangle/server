package net.bigtangle.tools.account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;

public class TokenCoinbase {
    
    public void syncTokenCoinbase(List<Map<String, Object>> tokens) {
        if (tokens.isEmpty()) return;
        lock.lock();
        try {
            Map<String, Coin> values = new HashMap<String, Coin>();
            for (Map<String, Object> map : tokens) {
                String tokenHex = (String) map.get("tokenHex");
                String value = map.get("value").toString();
                values.put(tokenHex, Coin.valueOf(Long.parseLong(value), tokenHex));
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
