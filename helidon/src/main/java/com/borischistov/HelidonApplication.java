package com.borischistov;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
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

    public static void main(String[] args) throws InterruptedException, ExecutionException, TimeoutException {
        var serverConfiguration = ServerConfiguration.builder().port(9000).build();
        WebServer
                .create(
                        serverConfiguration,
                        () -> Routing
                                .builder()
                                .any((req, resp) -> resp
                                        .status(Http.Status.OK_200)
                                        .send("Welcome from helidon"))
                                .build()
                )
                .start()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);
    }
}
