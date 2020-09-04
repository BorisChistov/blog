package com.borischistov;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.security.Role;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        checkAuth(route, serverRequest, serverResponse);
    }

    private void checkAuth(Route route, ServerRequest req, ServerResponse resp) {
        if (route.authenticate) {
            req.context().get(SecurityContext.class)
                .ifPresent(ctx -> ctx.atnClientBuilder()
                    .submit()
                    .whenComplete((res, t) -> {
                        if (t != null) {
                            resp.status(Http.Status.SERVICE_UNAVAILABLE_503).send(t.getMessage());
                            return;
                        }

                        if (!res.status().isSuccess()) {
                            resp.status(Http.Status.UNAUTHORIZED_401).send();
                            return;
                        }

                        if (route.authorize) {
                            if (res.user().isEmpty()) {
                                resp.status(Http.Status.UNAUTHORIZED_401).send();
                                return;
                            }

                            if (!route.roles.isEmpty()) {
                                var subject = res.user().get();
                                if (subject.grants(Role.class)
                                    .stream()
                                    .noneMatch(r -> route.roles.contains(r.getName()))) {
                                    resp.status(Http.Status.FORBIDDEN_403).send();
                                    return;
                                }
                            }
                        }


                        sendResponse(route, req, resp);
                    }));
        }
        else {
            sendResponse(route, req, resp);
        }
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
        private final Http.Status status;
        private final MediaType contentType;
        private final String body;
        private final boolean authorize;
        private final boolean authenticate;
        private final Set<String> roles;

        public Route(Config route) {
            this.authorize = route
                .get("authorize")
                .asBoolean()
                .orElse(false);
            this.authenticate = route
                .get("authenticate")
                .asBoolean()
                .orElse(false);
            this.roles = route
                .get("roles")
                .asList(String.class)
                .map(HashSet::new)
                .orElse(new HashSet<>());
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
