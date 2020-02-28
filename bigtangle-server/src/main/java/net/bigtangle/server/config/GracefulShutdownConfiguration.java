package net.bigtangle.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.server.shutdown.configure.GracefulShutdownHealthIndicator;
import net.bigtangle.server.shutdown.configure.GracefulShutdownProperties;
import net.bigtangle.server.shutdown.configure.GracefulShutdownTomcatConnectorCustomizer;
import net.bigtangle.server.shutdown.configure.GracefulShutdownTomcatContainerCustomizer;


@Configuration

public class GracefulShutdownConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public GracefulShutdownHealthIndicator gracefulShutdownHealthIndicator(
            ApplicationContext ctx, GracefulShutdownProperties props) {

        return new GracefulShutdownHealthIndicator(ctx, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public GracefulShutdownTomcatContainerCustomizer gracefulShutdownTomcatContainerCustomizer(
            GracefulShutdownTomcatConnectorCustomizer connectorCustomizer) {

        return new GracefulShutdownTomcatContainerCustomizer(connectorCustomizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public GracefulShutdownTomcatConnectorCustomizer gracefulShutdownTomcatConnectorCustomizer(
            ApplicationContext ctx, GracefulShutdownProperties props) {

        return new GracefulShutdownTomcatConnectorCustomizer(ctx, props);
    }
}
