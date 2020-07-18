/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class Lockobject {

    private String lockobjectid;
    private  String lockobjectclass;
    private  Long locktime;
    
    
    public Lockobject(String lockobjectid, String lockobjectclass, Long locktime) {
        super();
        this.lockobjectid = lockobjectid;
        this.lockobjectclass = lockobjectclass;
        this.locktime = locktime;
    }
    public String getLockobjectid() {
        return lockobjectid;
    }
    public void setLockobjectid(String lockobjectid) {
        this.lockobjectid = lockobjectid;
    }
    public String getLockobjectclass() {
        return lockobjectclass;
    }
    public void setLockobjectclass(String lockobjectclass) {
        this.lockobjectclass = lockobjectclass;
    }
    public Long getLocktime() {
        return locktime;
    }
    public void setLocktime(Long locktime) {
        this.locktime = locktime;
    }
    @Override
    public String toString() {
        return "Lockobject [lockobjectid=" + lockobjectid + ", lockobjectclass=" + lockobjectclass + ", locktime="
                + locktime + "]";
    }

}
