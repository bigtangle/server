/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.net.discovery;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VersionMessage;
import net.bigtangle.crawler.PeerSeedProtos;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A class that knows how to read signed sets of seeds over HTTP, using a simple protobuf based protocol. See the
 * peerseeds.proto file for the definition, with a gzipped delimited SignedPeerSeeds being the root of the data.
 * This is not currently in use by the Bitcoin community, but rather, is here for experimentation.
 */
public class HttpDiscovery implements PeerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(HttpDiscovery.class);

    public static class Details {
        @Nullable public final ECKey pubkey;
        public final URI uri;

        public Details(@Nullable ECKey pubkey, URI uri) {
            this.pubkey = pubkey;
            this.uri = uri;
        }
    }

    private final Details details;
    private final NetworkParameters params;
    private final OkHttpClient client;

    /**
     * Constructs a discovery object that will read data from the given HTTP[S] URI and, if a public key is provided,
     * will check the signature using that key.
     */
    public HttpDiscovery(NetworkParameters params, URI uri, @Nullable ECKey pubkey) {
        this(params, new Details(pubkey, uri));
    }

    /**
     * Constructs a discovery object that will read data from the given HTTP[S] URI and, if a public key is provided,
     * will check the signature using that key.
     */
    public HttpDiscovery(NetworkParameters params, Details details) {
        this(params, details, new OkHttpClient());
    }

    public HttpDiscovery(NetworkParameters params, Details details,  OkHttpClient client) {
        checkArgument(details.uri.getScheme().startsWith("http"));
        this.details = details;
        this.params = params;
        this.client = client;
    }

    @Override
    public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        try {
            HttpUrl.Builder url = HttpUrl.get(details.uri).newBuilder();
            if (services != 0)
                url.addQueryParameter("srvmask", Long.toString(services));
            Request.Builder request = new Request.Builder();
            request.url(url.build());
            request.addHeader("User-Agent", VersionMessage.LIBRARY_SUBVER); // TODO Add main version.
            log.info("Requesting seeds from {}", url);
            Response response = client.newCall(request.build()).execute();
            if (!response.isSuccessful())
                throw new PeerDiscoveryException("HTTP request failed: " + response.code() + " " + response.message());
            InputStream stream = response.body().byteStream();
            GZIPInputStream zip = new GZIPInputStream(stream);
            PeerSeedProtos.SignedPeerSeeds proto;
            try {
                proto = PeerSeedProtos.SignedPeerSeeds.parseDelimitedFrom(zip);
            } finally {
                zip.close(); // will close InputStream as well
            }

            return protoToAddrs(proto);
        } catch (PeerDiscoveryException e1) {
            throw e1;
        } catch (Exception e) {
            throw new PeerDiscoveryException(e);
        }
    }

    @VisibleForTesting
    public InetSocketAddress[] protoToAddrs(PeerSeedProtos.SignedPeerSeeds proto) throws PeerDiscoveryException, InvalidProtocolBufferException, SignatureException {
        if (details.pubkey != null) {
            if (!Arrays.equals(proto.getPubkey().toByteArray(), details.pubkey.getPubKey()))
                throw new PeerDiscoveryException("Public key mismatch");
            byte[] hash = Sha256Hash.hash(proto.getPeerSeeds().toByteArray());
            details.pubkey.verifyOrThrow(hash, proto.getSignature().toByteArray());
        }
        PeerSeedProtos.PeerSeeds seeds = PeerSeedProtos.PeerSeeds.parseFrom(proto.getPeerSeeds());
        if (seeds.getTimestamp() < Utils.currentTimeSeconds() - (60 * 60 * 24))
            throw new PeerDiscoveryException("Seed data is more than one day old: replay attack?");
      
        InetSocketAddress[] results = new InetSocketAddress[seeds.getSeedCount()];
        int i = 0;
        for (PeerSeedProtos.PeerSeedData data : seeds.getSeedList())
            results[i++] = new InetSocketAddress(data.getIpAddress(), data.getPort());
        return results;
    }

    @Override
    public void shutdown() {
    }
}
