/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package com.bignetcoin.store;

import net.bigtangle.core.BloomFilter;

/**
 * An interface which provides the information required to properly filter data downloaded from Peers.
 * Note that an implementer is responsible for calling {@link PeerGroup#recalculateFastCatchupAndFilter(org.bitcoinj.store.PeerGroup.FilterRecalculateMode)}
 * whenever a change occurs which effects the data provided via this interface.
 */
public interface PeerFilterProvider {
    /**
     * Returns the earliest timestamp (seconds since epoch) for which full/bloom-filtered blocks must be downloaded.
     * Blocks with timestamps before this time will only have headers downloaded. 0 requires that all blocks be
     * downloaded, and thus this should default to {@link System#currentTimeMillis()}/1000.
     */
    long getEarliestKeyCreationTime();

    /**
     * Called on all registered filter providers before getBloomFilterElementCount and getBloomFilter are called.
     * Once called, the provider should ensure that the items it will want to insert into the filter don't change.
     * The reason is that all providers will have their element counts queried, and then a filter big enough for
     * all of them will be specified. So the provider must use consistent state. There is guaranteed to be a matching
     * call to endBloomFilterCalculation that can be used to e.g. unlock a lock.
     */
    void beginBloomFilterCalculation();


    /**
     * Gets the number of elements that will be added to a bloom filter returned by
     * {@link PeerFilterProvider#getBloomFilter(int, double, long)}
     */
    int getBloomFilterElementCount();

    /**
     * Gets a bloom filter that contains all the necessary elements for the listener to receive relevant transactions.
     * Default value should be an empty bloom filter with the given size, falsePositiveRate, and nTweak.
     */
    BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak);

    /** Whether this filter provider depends on the server updating the filter on all matches */
    boolean isRequiringUpdateAllBloomFilter();

    void endBloomFilterCalculation();
}
