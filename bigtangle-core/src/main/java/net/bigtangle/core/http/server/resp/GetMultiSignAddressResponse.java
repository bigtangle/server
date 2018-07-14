package net.bigtangle.core.http.server.resp;

import java.util.List;

import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.http.AbstractResponse;

public class GetMultiSignAddressResponse extends AbstractResponse {

    public static GetMultiSignAddressResponse create(List<MultiSignAddress> list) {
        GetMultiSignAddressResponse res = new GetMultiSignAddressResponse();
        res.list = list;
        return res;
    }

    private List<MultiSignAddress> list;

    public List<MultiSignAddress> getList() {
        return list;
    }

    public void setList(List<MultiSignAddress> list) {
        this.list = list;
    }
}
