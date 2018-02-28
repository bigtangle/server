/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.params;

import org.bitcoinj.core.NetworkParameters;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.Set;

/**
 * Utility class that holds all the registered NetworkParameters types used for Address auto discovery.
 * By default only MainNetParams and TestNet3Params are used. If you want to use TestNet2, RegTestParams or
 * UnitTestParams use the register and unregister the TestNet3Params as they don't have their own address
 * version/type code.
 */
public class Networks {
    /** Registered networks */
    private static Set<? extends NetworkParameters> networks = ImmutableSet.of(TestNet3Params.get(), MainNetParams.get());

    public static Set<? extends NetworkParameters> get() {
        return networks;
    }

    public static void register(NetworkParameters network) {
        register(Lists.newArrayList(network));
    }

    public static void register(Collection<? extends NetworkParameters> networks) {
        ImmutableSet.Builder<NetworkParameters> builder = ImmutableSet.builder();
        builder.addAll(Networks.networks);
        builder.addAll(networks);
        Networks.networks = builder.build();
    }

    public static void unregister(NetworkParameters network) {
        if (networks.contains(network)) {
            ImmutableSet.Builder<NetworkParameters> builder = ImmutableSet.builder();
            for (NetworkParameters parameters : networks) {
                if (parameters.equals(network))
                    continue;
                builder.add(parameters);
            }
            networks = builder.build();
        }
    }
}
