/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.utils;

import java.util.List;
import java.util.Map;

public class HttpRequestParamUtil {

    public static int getParameterAsInt(Map<String, Object> request, String paramName) {
        int result = ((Integer) request.get(paramName)).intValue();
        return result;
    }
    
    public static String getParameterAsString(Map<String, Object> request, String paramName) {
        String result = (String) request.get(paramName);
        return result;
    }

    public static List<String> getParameterAsList(Map<String, Object> request, String paramName) {
        @SuppressWarnings("unchecked")
        final List<String> paramList = (List<String>) request.get(paramName);
        return paramList;
    }
}
