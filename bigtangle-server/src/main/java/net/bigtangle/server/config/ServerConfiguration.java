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

    private String mineraddress;

    private String serverversion;
    private String clientversion;
    private Boolean permissioned;
    private String permissionadmin;

    private Boolean myserverblockOnly = false;

    // does not reply all service request until service is set ready
    private Boolean serviceReady = false;

    public synchronized Boolean checkService() {
        if (!serviceReady) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
            }
        }
        return serviceReady;
    }

    public synchronized Boolean setServiceOK() {

        return serviceReady = true;
    }

    public synchronized Boolean setServiceWait() {

        return serviceReady = false;
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

}