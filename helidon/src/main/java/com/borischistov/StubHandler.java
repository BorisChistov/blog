package com.borischistov;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class StubHandler implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(StubHandler.class);

    private StubConfig stubConfig;

    public StubHandler(Config appConfig) {
        this.stubConfig = new StubConfig(appConfig);

        appConfig.onChange(newConfig -> {
            logger.debug("Config updated");
            this.stubConfig = new StubConfig(newConfig);
        });
    }

    @Override
    public void accept(
            ServerRequest serverRequest, ServerResponse serverResponse
    ) {
        var config = this.stubConfig;
        var route = config.routes.getOrDefault(serverRequest.path().toRawString(), config.notFound);
        sendResponse(route, serverRequest, serverResponse);
    }

    private void sendResponse(Route route, ServerRequest req, ServerResponse resp) {
        logger.debug(
                "Responding to path: {}, code: {}, content-type: {}, body: {}",
                req.path().toRawString(),
                route.status,
                route.contentType,
                route.body

        );
        resp.headers().contentType(route.contentType);
        resp.status(route.status).send(route.body);
    }

    @Getter
    @EqualsAndHashCode
    private static class StubConfig {
        private Route notFound;
        private Map<String, Route> routes;

        public StubConfig(Config appConfig) {
            this.notFound = new Route(appConfig.get("notFound"));
            this.routes = Collections.unmodifiableMap(
                    appConfig
                    .get("routes")
                    .traverse(c -> !c.isLeaf())
                    .peek(c -> logger.debug("Request mapping for path: {}", c.name()))
                    .collect(Collectors.toMap(Config::name, Route::new))
            );
        }
    }

    @Getter
    @EqualsAndHashCode
    private static class Route {
        private Http.Status status;
        private MediaType contentType;
        private String body;

        public Route(Config route) {
            this.status = route
                    .get("code")
                    .asInt()
                    .map(Http.Status::find)
                    .map(rc -> rc.orElse(Http.Status.OK_200))
                    .orElse(Http.Status.OK_200);
            this.contentType = route
                    .get("content-type")
                    .asString()
                    .map(MediaType::parse)
                    .orElse(MediaType.TEXT_PLAIN);
            this.body = route.get("body").asString().orElse("");
        }
    }
}
