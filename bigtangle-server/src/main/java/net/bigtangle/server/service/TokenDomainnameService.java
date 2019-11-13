package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetDomainTokenResponse;
import net.bigtangle.core.response.PermissionedAddressesResponse;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.DomainnameUtil;

@Service
public class TokenDomainnameService {

    @Autowired
    protected FullPrunedBlockStore store;
 
    @Autowired
    private NetworkParameters networkParameters;

    /**
     * query token type is domainname
     * 
     * @param domainNameBlockHash
     * @return
     * @throws BlockStoreException
     */

    public PermissionedAddressesResponse queryDomainnameTokenPermissionedAddresses(String domainNameBlockHash)
            throws BlockStoreException {
        if (domainNameBlockHash.equals(networkParameters.getGenesisBlock().getHashAsString())) {
            List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
            for (Iterator<PermissionDomainname> iterator =networkParameters.getPermissionDomainnameList()
                    .iterator(); iterator.hasNext();) {
                PermissionDomainname permissionDomainname = iterator.next();
                ECKey ecKey = permissionDomainname.getOutKey();
                multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
            }
            PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                    .create("", false, multiSignAddresses);
            return response;
        } else {
            Token token = this.store.getTokenByBlockHash(Sha256Hash.wrap(domainNameBlockHash));
            final String domainName = token.getTokenname();

            List<MultiSignAddress> multiSignAddresses = this
                    .queryDomainnameTokenMultiSignAddresses(token.getBlockHash());

            PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                    .create(domainName, false, multiSignAddresses);
            return response;
        }
    }

    /**
     * get domainname token multi sign address
     * 
     * @param domainNameBlockHash
     * @return
     * @throws BlockStoreException
     */
    public List<MultiSignAddress> queryDomainnameTokenMultiSignAddresses(Sha256Hash domainNameBlockHash)
            throws BlockStoreException {
        if (domainNameBlockHash.equals(networkParameters.getGenesisBlock().getHash())) {
            List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
            for (Iterator<PermissionDomainname> iterator = networkParameters.getPermissionDomainnameList()
                    .iterator(); iterator.hasNext();) {
                PermissionDomainname permissionDomainname = iterator.next();
                ECKey ecKey = permissionDomainname.getOutKey();
                multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
            }
            return multiSignAddresses;
        } else {
            Token token = this.store.queryDomainnameToken(domainNameBlockHash);
            if (token == null)
                throw new BlockStoreException("token not found");

            final String tokenid = token.getTokenid();
            List<MultiSignAddress> multiSignAddresses = this.store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokenid, token.getBlockHash());
            return multiSignAddresses;
        }
    }

    public AbstractResponse queryParentDomainnameBlockHash(String domainname) throws BlockStoreException {
        domainname = DomainnameUtil.matchParentDomainname(domainname);
        return queryDomainnameBlockHash(domainname);
    }

    public AbstractResponse queryDomainnameBlockHash(String domainname) throws BlockStoreException {
        AbstractResponse response;

        if (StringUtils.isBlank(domainname)) {

            response = GetDomainTokenResponse.createGetDomainBlockHashResponse(Token.genesisToken(networkParameters));
        } else {
            Token token = this.store.getTokensByDomainname(domainname);
            if (token == null) {
                throw new BlockStoreException("token domain name not found : " + domainname);
            }

            response = GetDomainTokenResponse.createGetDomainBlockHashResponse(token);
        }
        return response;
    }
}
