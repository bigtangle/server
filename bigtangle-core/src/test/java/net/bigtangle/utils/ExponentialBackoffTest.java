/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.utils;

import org.junit.Before;
import org.junit.Test;

import net.bigtangle.core.Utils;
import net.bigtangle.utils.ExponentialBackoff;

import java.util.PriorityQueue;

import static org.junit.Assert.*;

public class ExponentialBackoffTest {
    private ExponentialBackoff.Params params;
    private ExponentialBackoff backoff;

    @Before
    public void setUp() {
        Utils.setMockClock(System.currentTimeMillis() / 1000);
        params = new ExponentialBackoff.Params();
        backoff = new ExponentialBackoff(params);
    }

    @Test
    public void testSuccess() {
        assertEquals(Utils.currentTimeMillis(), backoff.getRetryTime());

        backoff.trackFailure();
        backoff.trackFailure();
        backoff.trackSuccess();

        assertEquals(Utils.currentTimeMillis(), backoff.getRetryTime());
    }

    @Test
    public void testFailure() {
        assertEquals(Utils.currentTimeMillis(), backoff.getRetryTime());

        backoff.trackFailure();
        backoff.trackFailure();
        backoff.trackFailure();

        assertEquals(Utils.currentTimeMillis() + 121, backoff.getRetryTime());
    }

    @Test
    public void testInQueue() {
        PriorityQueue<ExponentialBackoff> queue = new PriorityQueue<ExponentialBackoff>();
        ExponentialBackoff backoff1 = new ExponentialBackoff(params);
        backoff.trackFailure();
        backoff.trackFailure();
        backoff1.trackFailure();
        backoff1.trackFailure();
        backoff1.trackFailure();
        queue.offer(backoff);
        queue.offer(backoff1);

        assertEquals(queue.poll(), backoff); // The one with soonest retry time
        assertEquals(queue.peek(), backoff1);

        queue.offer(backoff);
        assertEquals(queue.poll(), backoff); // Still the same one
    }
}
