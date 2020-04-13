package net.bigtangle.docker;

public class Vmsystemportmap  { 
    private String vmname;
    private String hostname;

    private String porttype;
    private String activ = "Y";
    private Integer localport;
    private Integer   publicport;
    
    public String getVmname() {
        return vmname;
    }
    public void setVmname(String vmname) {
        this.vmname = vmname;
    }
    public String getHostname() {
        return hostname;
    }
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    public String getPorttype() {
        return porttype;
    }
    public void setPorttype(String porttype) {
        this.porttype = porttype;
    }
    public String getActiv() {
        return activ;
    }
    public void setActiv(String activ) {
        this.activ = activ;
    }
     
    public Integer getLocalport() {
        return localport;
    }
    public void setLocalport(Integer localport) {
        this.localport = localport;
    }
    public Integer getPublicport() {
        return publicport;
    }
    public void setPublicport(Integer publicport) {
        this.publicport = publicport;
    }
 

    
}
