package net.bigtangle.tools.account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.bigtangle.core.NetworkParameters;

public class TokenCoinbase {
    
    public void syncTokenCoinbase(List<Map<String, Object>> tokens) {
        if (tokens.isEmpty()) return;
        lock.lock();
        try {
            Map<String, Long> values = new HashMap<String, Long>();
            for (Map<String, Object> map : tokens) {
                String tokenHex = (String) map.get("tokenHex");
                String value = map.get("value").toString();
                values.put(tokenHex, Long.parseLong(value));
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
    
    private Map<String, Long> values = new HashMap<String, Long>();
    
    public long getCoinDefaultValue() {
        Long amount = this.values.get(NetworkParameters.BIGNETCOIN_TOKENID_STRING);
        if (amount == null) {
            return 0;
        }
        return amount.longValue();
    }
}
