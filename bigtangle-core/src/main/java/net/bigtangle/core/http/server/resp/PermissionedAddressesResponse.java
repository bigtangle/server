/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.http.AbstractResponse;

public class PermissionedAddressesResponse extends AbstractResponse {

    private List<MultiSignAddress> multiSignAddresses;

    public static AbstractResponse create(List<MultiSignAddress> multiSignAddresses) {
        PermissionedAddressesResponse res = new PermissionedAddressesResponse();
        res.multiSignAddresses = multiSignAddresses;
        return res;
    }

    public List<MultiSignAddress> getMultiSignAddresses() {
        return multiSignAddresses;
    }

    public void setMultiSignAddresses(List<MultiSignAddress> multiSignAddresses) {
        this.multiSignAddresses = multiSignAddresses;
    }
}
