package net.bigtangle.core.response;

public class GetDomainBlockHashResponse extends AbstractResponse {

    private String domainNameBlockHash;

    public String getdomainNameBlockHash() {
        return domainNameBlockHash;
    }

    public void setdomainNameBlockHash(String domainNameBlockHash) {
        this.domainNameBlockHash = domainNameBlockHash;
    }
    
    public static AbstractResponse createGetDomainBlockHashResponse(String domainNameBlockHash) {
        GetDomainBlockHashResponse res = new GetDomainBlockHashResponse();
        res.domainNameBlockHash = domainNameBlockHash;
        return res;
    }
}
