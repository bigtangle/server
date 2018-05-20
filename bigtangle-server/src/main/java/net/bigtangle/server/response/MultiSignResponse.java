package net.bigtangle.server.response;

import java.util.List;

import net.bigtangle.core.MultiSign;

public class MultiSignResponse extends AbstractResponse {

    public List<MultiSign> multiSigns;

    public List<MultiSign> getMultiSigns() {
        return multiSigns;
    }

    public void setMultiSigns(List<MultiSign> multiSigns) {
        this.multiSigns = multiSigns;
    }
    
    public static AbstractResponse createMultiSignResponse(List<MultiSign> multiSigns) {
        MultiSignResponse res = new MultiSignResponse();
        res.multiSigns = multiSigns;
        return res;
    }
}
