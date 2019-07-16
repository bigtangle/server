package net.bigtangle.server.service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.server.resp.GetDomainBlockHashResponse;
import net.bigtangle.core.http.server.resp.PermissionedAddressesResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockStore;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class TokenDomainnameService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private ServerConfiguration serverConfiguration;
    @Autowired
    private NetworkParameters networkParameters;

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDomainnameService.class);

    /**
     * query token type is domainname
     * 
     * @param domainPredecessorBlockHash
     * @return
     * @throws BlockStoreException
     */
    public PermissionedAddressesResponse queryDomainnameTokenPermissionedAddresses(String domainPredecessorBlockHash)
            throws BlockStoreException {
        List<MultiSignAddress> multiSignAddresses = this
                .queryDomainnameTokenMultiSignAddresses(domainPredecessorBlockHash);
        PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                .create(false, multiSignAddresses);
        return response;
    }

    /**
     * get domainname token multi sign address
     * 
     * @param domainPredecessorBlockHash
     * @return
     * @throws BlockStoreException
     */
    public List<MultiSignAddress> queryDomainnameTokenMultiSignAddresses(String domainPredecessorBlockHash)
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
            final String prevblockhash = token.getBlockhash();
            List<MultiSignAddress> multiSignAddresses = this.store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokenid, prevblockhash);
            return multiSignAddresses;
        }
    }

    public boolean checkTokenDomainnameAlreadyExists(String domainname) {
        try {
            int count = this.store.getCountTokenByDomainnameNumber(domainname);
            return count > 0;
        } catch (Exception e) {
            LOGGER.error("", e);
            return true;
        }
    }

    public AbstractResponse queryDomainnameTokenPredecessorBlockHash(String domainname) 
            throws BlockStoreException {
        AbstractResponse response;
        if (StringUtils.isBlank(domainname)) {
            String domainPredecessorBlockHash = networkParameters.getGenesisBlock().getHashAsString();
            response = GetDomainBlockHashResponse.createGetDomainBlockHashResponse(domainPredecessorBlockHash);
        } else {
            Token token = this.store.getTokensByDomainname(domainname);
            if (token == null) {
                throw new BlockStoreException("token domain name not found : " + domainname);
            }
            String domainPredecessorBlockHash = token.getBlockhash();
            response = GetDomainBlockHashResponse.createGetDomainBlockHashResponse(domainPredecessorBlockHash);
        }
        return response;
    }
}
