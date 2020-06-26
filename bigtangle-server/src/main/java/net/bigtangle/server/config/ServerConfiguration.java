/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
 
@Component
@ConfigurationProperties(prefix = "server")
public class ServerConfiguration {
    // sync seeds for other servers
    private String requester;
    // port of server
    private String port;

    // Mainnet or Test
    private String net;

    private String mineraddress;
    private String serverurl;
    private String serverversion;
    private String clientversion;
    private Boolean permissioned;
    private String permissionadmin;
    private int solveRewardduration = 50; // in seconds
    private Boolean myserverblockOnly = false;
    private long maxserachblocks = 5000;
  
    // does not reply all service request until service is set ready
    private Boolean serviceReady = false;
    private Boolean createtable = true;

    private double alphaMCMC = -0.05;
    private Boolean runKafkaStream = false;
    //start sync from this checkpoint
    private Long checkpoint=-1l ;
    private int syncblocks=500;
    
    private String indexhtml="https://www.bigtangle.org";
    private int blockPrototypeCachesSize =1;
    private String dockerDBHost="mysql-test";
    
    public synchronized Boolean checkService() {
        if (!serviceReady) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        }
        return serviceReady;
    }

    public synchronized void setServiceOK() {

        serviceReady = true;
    }

    public synchronized void setServiceWait() {

        serviceReady = false;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getNet() {
        return net;
    }

    public void setNet(String net) {
        this.net = net;
    }

    public String getMineraddress() {
        return mineraddress;
    }

    public void setMineraddress(String mineraddress) {
        this.mineraddress = mineraddress;
    }

    public String getServerversion() {
        return serverversion;
    }

    public void setServerversion(String serverversion) {
        this.serverversion = serverversion;
    }

    public String getClientversion() {
        return clientversion;
    }

    public void setClientversion(String clientversion) {
        this.clientversion = clientversion;
    }

    public Boolean getPermissioned() {
        return permissioned;
    }

    public void setPermissioned(Boolean permissioned) {
        this.permissioned = permissioned;
    }

    public String getPermissionadmin() {
        return permissionadmin;
    }

    public void setPermissionadmin(String permissionadmin) {
        this.permissionadmin = permissionadmin;
    }

    public Boolean getMyserverblockOnly() {
        return myserverblockOnly;
    }

    public void setMyserverblockOnly(Boolean myserverblockOnly) {
        this.myserverblockOnly = myserverblockOnly;
    }

    public Boolean getServiceReady() {
        return serviceReady;
    }

    public void setServiceReady(Boolean serviceReady) {
        this.serviceReady = serviceReady;
    }

    public Boolean getCreatetable() {
        return createtable;
    }

    public void setCreatetable(Boolean createtable) {
        this.createtable = createtable;
    }
 

    public String getServerurl() {
        return serverurl;
    }

    public void setServerurl(String serverurl) {
        this.serverurl = serverurl;
    }

    public int getSolveRewardduration() {
        return solveRewardduration;
    }

    public void setSolveRewardduration(int solveRewardduration) {
        this.solveRewardduration = solveRewardduration;
    }

    public long getMaxserachblocks() {
        return maxserachblocks;
    }

    public void setMaxserachblocks(long maxserachblocks) {
        this.maxserachblocks = maxserachblocks;
    }

    public double getAlphaMCMC() {
        return alphaMCMC;
    }

    public void setAlphaMCMC(double alphaMCMC) {
        this.alphaMCMC = alphaMCMC;
    }

    public Boolean getRunKafkaStream() {
        return runKafkaStream;
    }

    public void setRunKafkaStream(Boolean runKafkaStream) {
        this.runKafkaStream = runKafkaStream;
    }

    public Long getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Long checkpoint) {
        this.checkpoint = checkpoint;
    }

    public int getSyncblocks() {
        return syncblocks;
    }

    public void setSyncblocks(int syncblocks) {
        this.syncblocks = syncblocks;
    }

    public String getIndexhtml() {
        return indexhtml;
    }

    public void setIndexhtml(String indexhtml) {
        this.indexhtml = indexhtml;
    }

    public int getBlockPrototypeCachesSize() {
        return blockPrototypeCachesSize;
    }

    public void setBlockPrototypeCachesSize(int blockPrototypeCachesSize) {
        this.blockPrototypeCachesSize = blockPrototypeCachesSize;
    }

    public String getDockerDBHost() {
        return dockerDBHost;
    }

    public void setDockerDBHost(String dockerDBHost) {
        this.dockerDBHost = dockerDBHost;
    }

    @Override
    public String toString() {
        return "ServerConfiguration [requester=" + requester + ", port=" + port + ", net=" + net + ", mineraddress="
                + mineraddress + ", serverurl=" + serverurl + ", serverversion=" + serverversion + ", clientversion="
                + clientversion + ", permissioned=" + permissioned + ", permissionadmin=" + permissionadmin
                + ", solveRewardduration=" + solveRewardduration + ", myserverblockOnly=" + myserverblockOnly
                + ", maxserachblocks=" + maxserachblocks  
                + ", serviceReady=" + serviceReady + ", createtable=" + createtable + ", alphaMCMC=" + alphaMCMC
                + ", runKafkaStream=" + runKafkaStream + "]";
    }

}