package net.bigtangle.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;

@Configuration

public class CachingConfig {
    //
    // @Bean
    // public CacheManager cacheManager() {
    // return new ConcurrentMapCacheManager("blocks");
    // }

    @Bean
    public Config hazelCastConfig() {

        Config config = new Config();
        config.setInstanceName("hazelcast-instance")
                .addMapConfig(new MapConfig().setName("configuration")
                        .setMaxSizeConfig(new MaxSizeConfig(200, MaxSizeConfig.MaxSizePolicy.FREE_HEAP_SIZE))
                        .setEvictionPolicy(EvictionPolicy.LRU).setTimeToLiveSeconds(3600));
        return config;

    }
}
