package net.bigtangle.server.config;

import java.util.ArrayList;
import java.util.List;

public class LockAddress {

    String lockaddress;
    Long height;
    public static List<LockAddress> init() {
      List<LockAddress> re= new ArrayList<LockAddress>();
       LockAddress a = new LockAddress("1718BePa7qEhNb24gyw3xZDvjsrCa6it5H", 700000L);
       re.add(a);
        return re;
    }
    public LockAddress(String lockaddress, Long height) {
        super();
        this.lockaddress = lockaddress;
        this.height = height;
    }
    public String getLockaddress() {
        return lockaddress;
    }
    public void setLockaddress(String lockaddress) {
        this.lockaddress = lockaddress;
    }
    public Long getHeight() {
        return height;
    }
    public void setHeight(Long height) {
        this.height = height;
    }
    
}
