/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *******************************************************************************/
package net.bigtangle.utils;

import org.apache.commons.lang3.StringUtils;

public class DomainnameUtil {

    public static String matchParentDomainname(String domainname) {
        if (StringUtils.isBlank(domainname)) {
            return "";
        }
        if (domainname.indexOf(".") < 0) {
            return "";
        }
        String str = domainname.substring(domainname.indexOf(".") + 1);
        return str;
    }
}
