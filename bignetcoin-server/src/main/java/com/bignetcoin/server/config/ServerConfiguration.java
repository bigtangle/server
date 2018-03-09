/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.config;


import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "server")
public class ServerConfiguration {

 
    private String neighbors;

    @NotNull
    private String port;

    private String udp_receiver_port;

    private String tcp_receiver_port;

    private Boolean debug;

    @NotNull
    private Boolean testnet;

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

    public String getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(String neighbors) {
        this.neighbors = neighbors;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUdp_receiver_port() {
        return udp_receiver_port;
    }

    public void setUdp_receiver_port(String udp_receiver_port) {
        this.udp_receiver_port = udp_receiver_port;
    }

    public String getTcp_receiver_port() {
        return tcp_receiver_port;
    }

    public void setTcp_receiver_port(String tcp_receiver_port) {
        this.tcp_receiver_port = tcp_receiver_port;
    }

    public Boolean getDebug() {
        return debug;
    }

    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

    public Boolean getTestnet() {
        return testnet;
    }

    public void setTestnet(Boolean testnet) {
        this.testnet = testnet;
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

    
    
}