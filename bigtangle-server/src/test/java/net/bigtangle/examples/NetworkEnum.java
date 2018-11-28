/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.examples;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.MainNetParams;

public enum NetworkEnum {
    MAIN,
    PROD, // alias for MAIN
    TEST,
    REGTEST;

    public NetworkParameters get() {
        switch(this) {
            case MAIN:
            case PROD:
                return MainNetParams.get();
            case TEST:
                return MainNetParams.get(); 
            default:
                return MainNetParams.get();
        }
    }
}
