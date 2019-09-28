package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetDomainBlockHashResponse;
import net.bigtangle.core.http.server.resp.PermissionedAddressesResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.DomainnameUtil;

@Service
public class TokenDomainnameService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private ServerConfiguration serverConfiguration;
    @Autowired
    private NetworkParameters networkParameters;

    /**
     * query token type is domainname
     * 
     * @param domainPredecessorBlockHash
     * @return
     * @throws BlockStoreException
     */
    @Cacheable("queryDomainnameTokenPermissionedAddresses")
    public PermissionedAddressesResponse queryDomainnameTokenPermissionedAddresses(String domainPredecessorBlockHash)
            throws BlockStoreException {
        Token token = this.store.getToken(Sha256Hash.wrap(domainPredecessorBlockHash));
        final String domainName = token.getDomainName();

        List<MultiSignAddress> multiSignAddresses = this
                .queryDomainnameTokenMultiSignAddresses(token.getBlockHash());

        PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                .create(domainName, false, multiSignAddresses);
        return response;
    }

    /**
     * get domainname token multi sign address
     * 
     * @param domainPredecessorBlockHash
     * @return
     * @throws BlockStoreException
     */
    public List<MultiSignAddress> queryDomainnameTokenMultiSignAddresses(Sha256Hash domainPredecessorBlockHash)
            throws BlockStoreException {
        if (domainPredecessorBlockHash.equals(networkParameters.getGenesisBlock().getHashAsString())) {
            List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
            for (Iterator<PermissionDomainname> iterator = this.serverConfiguration.getPermissionDomainname()
                    .iterator(); iterator.hasNext();) {
                PermissionDomainname permissionDomainname = iterator.next();
                ECKey ecKey = permissionDomainname.getOutKey();
                multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
            }
            return multiSignAddresses;
        } else {
            Token token = this.store.queryDomainnameToken(domainPredecessorBlockHash);
            if (token == null)
                throw new BlockStoreException("token not found");

            final String tokenid = token.getTokenid(); 
            List<MultiSignAddress> multiSignAddresses = this.store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokenid, token.getBlockHash());
            return multiSignAddresses;
        }
    }
   
    public AbstractResponse queryDomainnameTokenPredecessorBlockHash(String domainname) 
            throws BlockStoreException {
        AbstractResponse response;
        domainname = DomainnameUtil.matchParentDomainname(domainname);
        if (StringUtils.isBlank(domainname)) {
            String domainPredecessorBlockHash = networkParameters.getGenesisBlock().getHashAsString();
            response = GetDomainBlockHashResponse.createGetDomainBlockHashResponse(domainPredecessorBlockHash);
        } else {
            Token token = this.store.getTokensByDomainname(domainname);
            if (token == null) {
                throw new BlockStoreException("token domain name not found : " + domainname);
            }
            String domainPredecessorBlockHash = token.getBlockHashHex();
            response = GetDomainBlockHashResponse.createGetDomainBlockHashResponse(domainPredecessorBlockHash);
        }
        return response;
    }
}
