/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "server")
public class ServerConfiguration {

 
    private String requester;
    
    private String port; 
 
    private Boolean debug;

    @Value("${net:Mainnet}")
    private String net;

    private String remote;

    private String remote_auth;

    private long remote_limit_api;
    private String mineraddress;
    
    @Value("${minRandomWalks:5}")
    private   int minRandomWalks;
    @Value("${maxRandomWalks:27}")
    private   int maxRandomWalks;
    @Value("${maxFindTxs:100000}")
    private   int maxFindTxs;
    @Value("${maxRequestList:1000}")
    private   int maxRequestList;

    private String serverversion;
    private String clientversion;
    private Boolean permissioned;  
    
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

     
    public Boolean getDebug() {
        return debug;
    }

    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

  
    public String getNet() {
        return net;
    }

    public void setNet(String net) {
        this.net = net;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getRemote_auth() {
        return remote_auth;
    }

    public void setRemote_auth(String remote_auth) {
        this.remote_auth = remote_auth;
    }

    public long getRemote_limit_api() {
        return remote_limit_api;
    }

    public void setRemote_limit_api(long remote_limit_api) {
        this.remote_limit_api = remote_limit_api;
    }

    public int getMinRandomWalks() {
        return minRandomWalks;
    }

    public void setMinRandomWalks(int minRandomWalks) {
        this.minRandomWalks = minRandomWalks;
    }

    public int getMaxRandomWalks() {
        return maxRandomWalks;
    }

    public void setMaxRandomWalks(int maxRandomWalks) {
        this.maxRandomWalks = maxRandomWalks;
    }

    public int getMaxFindTxs() {
        return maxFindTxs;
    }

    public void setMaxFindTxs(int maxFindTxs) {
        this.maxFindTxs = maxFindTxs;
    }

    public int getMaxRequestList() {
        return maxRequestList;
    }

    public void setMaxRequestList(int maxRequestList) {
        this.maxRequestList = maxRequestList;
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

    
    
}