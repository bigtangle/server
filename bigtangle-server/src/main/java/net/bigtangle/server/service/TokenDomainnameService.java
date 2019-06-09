package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.Token;
import net.bigtangle.core.exception.BlockStoreException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDomainnameService.class);

    /**
     * query token type is domainname
     * 
     * @param domainname
     * @return
     * @throws BlockStoreException
     */
    public PermissionedAddressesResponse queryDomainnameTokenPermissionedAddresses(String domainname)
            throws BlockStoreException {
        domainname = DomainnameUtil.matchParentDomainname(domainname);
        if (StringUtils.isBlank(domainname)) {
            List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
            for (Iterator<PermissionDomainname> iterator = this.serverConfiguration.getPermissionDomainname()
                    .iterator(); iterator.hasNext();) {
                PermissionDomainname permissionDomainname = iterator.next();
                ECKey ecKey = permissionDomainname.getOutKey();
                multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
            }
            PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                    .create(true, multiSignAddresses);
            return response;
        } else {
            Token token = this.store.queryDomainnameToken(domainname);
            if (token == null)
                throw new BlockStoreException("token not found");

            final String tokenid = token.getTokenid();
            final String prevblockhash = token.getBlockhash();
            List<MultiSignAddress> multiSignAddresses = this.store
                    .getMultiSignAddressListByTokenidAndBlockHashHex(tokenid, prevblockhash);

            PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
                    .create(false, multiSignAddresses);
            return response;
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
}
