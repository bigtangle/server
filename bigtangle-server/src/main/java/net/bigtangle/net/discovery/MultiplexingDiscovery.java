/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.net.discovery;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VersionMessage;
import net.bigtangle.net.discovery.DnsDiscovery.DnsSeedDiscovery;
import net.bigtangle.net.discovery.HttpDiscovery.Details;
import net.bigtangle.utils.ContextPropagatingThreadFactory;
import okhttp3.OkHttpClient;

/**
 * MultiplexingDiscovery queries multiple PeerDiscovery objects, shuffles their responses and then returns the results,
 * thus selecting randomly between them and reducing the influence of any particular seed. Any that don't respond
 * within the timeout are ignored. Backends are queried in parallel. Backends may block.
 */
public class MultiplexingDiscovery implements PeerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(MultiplexingDiscovery.class);

    protected final List<PeerDiscovery> seeds;
    protected final NetworkParameters netParams;
    private volatile ExecutorService vThreadPool;

    /**
     * Builds a suitable set of peer discoveries. Will query them in parallel before producing a merged response.
     * If specific services are required, DNS is not used as the protocol can't handle it.
     * @param params Network to use.
     * @param services Required services as a bitmask, e.g. {@link VersionMessage#NODE_NETWORK}.
     */
    public static MultiplexingDiscovery forServices(NetworkParameters params, long services) {
        List<PeerDiscovery> discoveries = Lists.newArrayList();
        Details[] httpSeeds = new HttpDiscovery.Details[] {
                // Andreas Schildbach
                new HttpDiscovery.Details(
                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
                )
        }; 
        if (httpSeeds != null) {
            OkHttpClient httpClient = new OkHttpClient();
            for (HttpDiscovery.Details httpSeed : httpSeeds)
                discoveries.add(new HttpDiscovery(params, httpSeed, httpClient));
        }
        // Also use DNS seeds if there is no specific service requirement
        if (services == 0) {
            String[] dnsSeeds = params.getDnsSeeds();
            if (dnsSeeds != null)
                for (String dnsSeed : dnsSeeds)
                    discoveries.add(new DnsSeedDiscovery(params, dnsSeed));
        }
        return new MultiplexingDiscovery(params, discoveries);
    }

    /**
     * Will query the given seeds in parallel before producing a merged response.
     */
    public MultiplexingDiscovery(NetworkParameters params, List<PeerDiscovery> seeds) {
        checkArgument(!seeds.isEmpty());
        this.netParams = params;
        this.seeds = seeds;
    }

    @Override
    public InetSocketAddress[] getPeers(final long services, final long timeoutValue, final TimeUnit timeoutUnit) throws PeerDiscoveryException {
        vThreadPool = createExecutor();
        try {
            List<Callable<InetSocketAddress[]>> tasks = Lists.newArrayList();
            for (final PeerDiscovery seed : seeds) {
                tasks.add(new Callable<InetSocketAddress[]>() {
                    @Override
                    public InetSocketAddress[] call() throws Exception {
                        return seed.getPeers(services, timeoutValue,  timeoutUnit);
                    }
                });
            }
            final List<Future<InetSocketAddress[]>> futures = vThreadPool.invokeAll(tasks, timeoutValue, timeoutUnit);
            ArrayList<InetSocketAddress> addrs = Lists.newArrayList();
            for (int i = 0; i < futures.size(); i++) {
                Future<InetSocketAddress[]> future = futures.get(i);
                if (future.isCancelled()) {
                    log.warn("Seed {}: timed out", seeds.get(i));
                    continue;  // Timed out.
                }
                final InetSocketAddress[] inetAddresses;
                try {
                    inetAddresses = future.get();
                } catch (ExecutionException e) {
                    log.warn("Seed {}: failed to look up: {}", seeds.get(i), e.getMessage());
                    continue;
                }
                Collections.addAll(addrs, inetAddresses);
            }
            if (addrs.size() == 0)
                throw new PeerDiscoveryException("No peer discovery returned any results in "
                        + timeoutUnit.toMillis(timeoutValue) + "ms. Check internet connection?");
            Collections.shuffle(addrs);
            vThreadPool.shutdownNow();
            return addrs.toArray(new InetSocketAddress[addrs.size()]);
        } catch (InterruptedException e) {
            throw new PeerDiscoveryException(e);
        } finally {
            vThreadPool.shutdown();
        }
    }

    protected ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(seeds.size(), new ContextPropagatingThreadFactory("Multiplexing discovery"));
    }

    @Override
    public void shutdown() {
        ExecutorService tp = vThreadPool;
        if (tp != null)
            tp.shutdown();
    }
}
