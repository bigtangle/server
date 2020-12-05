/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.pool.server;

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

    @Override
    public String toString() {
        return "ServerState [serverurl=" + serverurl + ", status=" + status + ", chainlength=" + chainlength
                + ", responseTime=" + responseTime + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((chainlength == null) ? 0 : chainlength.hashCode());
        result = prime * result + ((responseTime == null) ? 0 : responseTime.hashCode());
        result = prime * result + ((serverurl == null) ? 0 : serverurl.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ServerState other = (ServerState) obj;
        if (chainlength == null) {
            if (other.chainlength != null)
                return false;
        } else if (!chainlength.equals(other.chainlength))
            return false;
        if (responseTime == null) {
            if (other.responseTime != null)
                return false;
        } else if (!responseTime.equals(other.responseTime))
            return false;
        if (serverurl == null) {
            if (other.serverurl != null)
                return false;
        } else if (!serverurl.equals(other.serverurl))
            return false;
        if (status == null) {
            if (other.status != null)
                return false;
        } else if (!status.equals(other.status))
            return false;
        return true;
    }

    

}
