package net.bigtangle.docker;

public class VmConfigfile {
    public String vmname;
    public String filename;
    public byte[] config;
    public String description;
    public String active;
    public String changedby;
    public java.sql.Timestamp changeddate;
    public String changedTolast;
    public String getVmname() {
        return vmname;
    }
    public void setVmname(String vmname) {
        this.vmname = vmname;
    }
    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public byte[] getConfig() {
        return config;
    }
    public void setConfig(byte[] config) {
        this.config = config;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getActive() {
        return active;
    }
    public void setActive(String active) {
        this.active = active;
    }
    public String getChangedby() {
        return changedby;
    }
    public void setChangedby(String changedby) {
        this.changedby = changedby;
    }
    public java.sql.Timestamp getChangeddate() {
        return changeddate;
    }
    public void setChangeddate(java.sql.Timestamp changeddate) {
        this.changeddate = changeddate;
    }
    public String getChangedTolast() {
        return changedTolast;
    }
    public void setChangedTolast(String changedTolast) {
        this.changedTolast = changedTolast;
    }
    
    
}
