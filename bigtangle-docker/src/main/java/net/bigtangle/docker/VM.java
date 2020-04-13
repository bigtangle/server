package net.bigtangle.docker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

 

public class VM   implements   Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public String name;
    public Long memoryMB;
    public String networkName;
    public String ip;
    public List<String> volumns;
    public List<Vmsystemportmap> listVmsystemportmap = new ArrayList<Vmsystemportmap>();

    public List<VmConfigfile>  listVmConfigfile = new ArrayList<VmConfigfile>();
    
    public String getName() {
        return name;
    }

    public void setName(String nameLabel) {
        this.name = nameLabel;
    }

    public Long getMemoryMB() {
        return memoryMB;
    }

    public void setMemoryMB(Long memoryMB) {
        this.memoryMB = memoryMB;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<Vmsystemportmap> getListVmsystemportmap() {
        return listVmsystemportmap;
    }

    public void setListVmsystemportmap(List<Vmsystemportmap> listVmsystemportmap) {
        this.listVmsystemportmap = listVmsystemportmap;
    }

    public List<String> getVolumns() {
        return volumns;
    }

    public void setVolumns(List<String> volumns) {
        this.volumns = volumns;
    }

    public List<VmConfigfile> getListVmConfigfile() {
        return listVmConfigfile;
    }

    public void setListVmConfigfile(List<VmConfigfile> listVmConfigfile) {
        this.listVmConfigfile = listVmConfigfile;
    }

}
