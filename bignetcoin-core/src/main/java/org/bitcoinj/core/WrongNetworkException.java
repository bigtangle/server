/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.util.Arrays;

/**
 * This exception is thrown by the Address class when you try and decode an address with a version code that isn't
 * used by that network. You shouldn't allow the user to proceed in this case as they are trying to send money across
 * different chains, an operation that is guaranteed to destroy the money.
 */
public class WrongNetworkException extends AddressFormatException {
    /** The version code that was provided in the address. */
    public int verCode;
    /** The list of acceptable versions that were expected given the addresses network parameters. */
    public int[] acceptableVersions;
    
    public WrongNetworkException(int verCode, int[] acceptableVersions) {
        super("Version code of address did not match acceptable versions for network: " + verCode + " not in " +
          Arrays.toString(acceptableVersions));
        this.verCode = verCode;
        this.acceptableVersions = acceptableVersions;
    }
}
