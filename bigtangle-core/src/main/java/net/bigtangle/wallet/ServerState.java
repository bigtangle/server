/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

/*
 * Statistic data from the server
 */
public class ServerState {

    // this the server url
    public String serverurl;
    // status of the server, health check
    public String status;
    // last chain length
    public Long chainlength;
    //  response time for get chain number in milliseconds
    public Long responseTime;

    public String getServerurl() {
        return serverurl;
    }

    public void setServerurl(String serverurl) {
        this.serverurl = serverurl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getChainlength() {
        return chainlength;
    }

    public void setChainlength(Long chainlength) {
        this.chainlength = chainlength;
    }

    public Long getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }

    

}
