package net.bigtangle.server.config;

import java.util.ArrayList;
import java.util.List;

public class BurnedAddress {

    String lockaddress;
    Long chain;

    public static List<BurnedAddress> init() {
        List<BurnedAddress> re = new ArrayList<BurnedAddress>();
        BurnedAddress a = new BurnedAddress("1718BePa7qEhNb24gyw3xZDvjsrCa6it5H", 549937L);
        re.add(a);

        a = new BurnedAddress("1MSHr1E2n8XogsPs2bP5qkzB6AUTpzXaQS", 552561L);
        re.add(a);

        a = new BurnedAddress("19tGEFb1ghRDsp2jCfB7rZYJiM23QHPxZi", 552561L);
        re.add(a);

        a = new BurnedAddress("19tJCQa3ioY172rXhRKwGCCBXnoFFVDYTJ", 552561L);
        re.add(a);

        a = new BurnedAddress("141JK2qkCRJxZbFZ9qbocRvMMpEEGa4tGj", 552561L);
        re.add(a);
        
        a = new BurnedAddress("1MymcnqHbDErpj8fvt2eDnn1bmFqsPh65S", 552561L);
        re.add(a); 
        
        a = new BurnedAddress("1Hd9oqy9WBJUy3ApAvL5Zkjr8uiB4cKSaL", 552561L);
        re.add(a); 
        
        a = new BurnedAddress("1ACA4Yt4YYwCxiVDR2SpcYN7XuvAeCGU7s", 552561L);
        re.add(a); 
        
        // for test
        a = new BurnedAddress("1PqtKWvCUuPJf9YDK2WQAXqb3aeoav42Yh", 552561L);
        re.add(a);

        return re;
    }

    public BurnedAddress(String lockaddress, Long chain) {
        super();
        this.lockaddress = lockaddress;
        this.chain = chain;
    }

    public String getLockaddress() {
        return lockaddress;
    }

    public void setLockaddress(String lockaddress) {
        this.lockaddress = lockaddress;
    }

    public Long getChain() {
        return chain;
    }

    public void setChain(Long chain) {
        this.chain = chain;
    }

}
