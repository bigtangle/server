/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PermissionDomainname;

@Component
@ConfigurationProperties(prefix = "server")
public class ServerConfiguration {

    private String requester;

    private String port;

    private Boolean debug;

    private String net;

    private String mineraddress;
    private String serverurl;
    private String serverversion;
    private String clientversion;
    private Boolean permissioned;
    private String permissionadmin;

    private Boolean myserverblockOnly = false;

    private List<PermissionDomainname> permissionDomainname = ImmutableList
            .of(new PermissionDomainname(NetworkParameters.testPub, ""));

    // does not reply all service request until service is set ready
    private Boolean serviceReady = false;
    private Boolean createtable = true;

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

    public Boolean getCreatetable() {
        return createtable;
    }

    public void setCreatetable(Boolean createtable) {
        this.createtable = createtable;
    }

    public List<PermissionDomainname> getPermissionDomainname() {
        return permissionDomainname;
    }

    public void setPermissionDomainname(List<PermissionDomainname> permissionDomainname) {
        this.permissionDomainname = permissionDomainname;
    }

    public String getServerurl() {
        return serverurl;
    }

    public void setServerurl(String serverurl) {
        this.serverurl = serverurl;
    }

    @Override
    public String toString() {
        return "ServerConfiguration [requester=" + requester + ", port=" + port + ", debug=" + debug + ", net=" + net
                + ", mineraddress=" + mineraddress + ", serverurl=" + serverurl + ", serverversion=" + serverversion
                + ", clientversion=" + clientversion + ", permissioned=" + permissioned + ", permissionadmin="
                + permissionadmin + ", myserverblockOnly=" + myserverblockOnly + ", permissionDomainname="
                + permissionDomainname + ", serviceReady=" + serviceReady + ", createtable=" + createtable + "]";
    }
    
}