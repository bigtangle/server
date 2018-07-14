package net.bigtangle.core.http.server.resp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.http.AbstractResponse;

public class SearchMultiSignResponse extends AbstractResponse {

    public static AbstractResponse createSearchMultiSignResponse(List<Map<String, Object>> multiSignList) {
        SearchMultiSignResponse res = new SearchMultiSignResponse();
        res.multiSignList = multiSignList;
        return res;
    }

    private List<Map<String, Object>> multiSignList = new ArrayList<Map<String, Object>>();

    public List<Map<String, Object>> getMultiSignList() {
        return multiSignList;
    }

    public void setMultiSignList(List<Map<String, Object>> multiSignList) {
        this.multiSignList = multiSignList;
    }
}
