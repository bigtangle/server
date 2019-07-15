package net.bigtangle.core.http.server.resp;

import net.bigtangle.core.http.AbstractResponse;

public class GetDomainBlockHashResponse extends AbstractResponse {

    private String domainPredecessorBlockHash;

    public String getDomainPredecessorBlockHash() {
        return domainPredecessorBlockHash;
    }

    public void setDomainPredecessorBlockHash(String domainPredecessorBlockHash) {
        this.domainPredecessorBlockHash = domainPredecessorBlockHash;
    }
    
    public static AbstractResponse createGetDomainBlockHashResponse(String domainPredecessorBlockHash) {
        GetDomainBlockHashResponse res = new GetDomainBlockHashResponse();
        res.domainPredecessorBlockHash = domainPredecessorBlockHash;
        return res;
    }
}
