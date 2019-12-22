package com.borischistov;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;

public class HelidonApplication {

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    private static final Logger logger = LoggerFactory.getLogger(HelidonApplication.class);

    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {
        var config = Config
                .builder()
                .sources(
                        ConfigSources
                                .classpath("application.yaml")
                                .pollingStrategy(PollingStrategies::nop),
                        ConfigSources
                                .file("conf/application.yaml")
                                .pollingStrategy(PollingStrategies::watch)
                                .optional()
                )
                .build();
        var serverConfig = config.get("server");
        var appConfig = config.get("app");

        var serverConfiguration = ServerConfiguration.builder(serverConfig).build();
        var ws = WebServer
                .create(
                        serverConfiguration,
                        () -> Routing.builder().any(new StubHandler(appConfig)).build()
                )
                .start()
                .toCompletableFuture()
                .get(
                    2,
                    TimeUnit.SECONDS
                );

        logger.info(
                "Server started, address: http://localhost:{}",
                ws.port()
        );
    }


}
