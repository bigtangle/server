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
    
    private boolean isRootPermissioned;

    public static AbstractResponse create(boolean isRootPermissioned, List<MultiSignAddress> multiSignAddresses) {
        PermissionedAddressesResponse res = new PermissionedAddressesResponse();
        res.isRootPermissioned = isRootPermissioned;
        res.multiSignAddresses = multiSignAddresses;
        return res;
    }

    public List<MultiSignAddress> getMultiSignAddresses() {
        return multiSignAddresses;
    }

    public void setMultiSignAddresses(List<MultiSignAddress> multiSignAddresses) {
        this.multiSignAddresses = multiSignAddresses;
    }

    public boolean isRootPermissioned() {
        return isRootPermissioned;
    }

    public void setRootPermissioned(boolean isRootPermissioned) {
        this.isRootPermissioned = isRootPermissioned;
    }
}
