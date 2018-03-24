/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.utils;

import org.bitcoinj.core.NetworkParameters;

/**
 * Caching counter for the block versions within a moving window. This class
 * is NOT thread safe (as if two threads are trying to use it concurrently,
 * there's risk of getting versions out of sequence).
 *
 * @see org.bitcoinj.core.NetworkParameters#getMajorityWindow()
 * @see org.bitcoinj.core.NetworkParameters#getMajorityEnforceBlockUpgrade()
 * @see org.bitcoinj.core.NetworkParameters#getMajorityRejectBlockOutdated()
 */
public class VersionTally {
    /**
     * Cache of version numbers.
     */
    private final long[] versionWindow;

    /**
     * Offset within the version window at which the next version will be
     * written.
     */
    private int versionWriteHead = 0;

    /**
     * Number of versions written into the tally. Until this matches the length
     * of the version window, we do not have sufficient data to return values.
     */
    private int versionsStored = 0;

    public VersionTally(final NetworkParameters params) {
        versionWindow = new long[params.getMajorityWindow()];
    }

    /**
     * Add a new block version to the tally, and return the count for that version
     * within the window.
     *
     * @param version the block version to add.
     */
    public void add(final long version) {
        versionWindow[versionWriteHead++] = version;
        if (versionWriteHead == versionWindow.length) {
            versionWriteHead = 0;
        }
        versionsStored++;
    }

    /**
     * Get the count of blocks at or above the given version, within the window.
     *
     * @param version the block version to query.
     * @return the count for the block version, or null if the window is not yet
     * full.
     */
    public Integer getCountAtOrAbove(final long version) {
        if (versionsStored < versionWindow.length) {
            return null;
        }
        int count = 0;
        for (int versionIdx = 0; versionIdx < versionWindow.length; versionIdx++) {
            if (versionWindow[versionIdx] >= version) {
                count++;
            }
        }

        return count;
    }
 

    /**
     * Get the size of the version window.
     */
    public int size() {
        return versionWindow.length;
    }
}
