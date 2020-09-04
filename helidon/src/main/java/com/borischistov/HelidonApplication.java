package com.borischistov;

import io.helidon.common.configurable.Resource;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.media.jackson.JacksonSupport;
import io.helidon.security.Role;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.webserver.WebSecurity;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.security.providers.jwt.JwtProvider;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
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
                    .classpath("application.yaml"),
                ConfigSources
                    .file("conf/application.yaml")
                    .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(5)))
                    .optional()
            )
            .build();
        var appConfig = config.get("app");
        var keySet = JwkKeys.builder().resource(Resource.create(Paths.get("conf/jwks.json"))).build();
        var provider = JwtProvider.builder()
            .verifyJwk(Resource.create(Paths.get("conf/jwks.json")))
            .build();
        var security = Security.builder()
            .addProvider(provider)
            .build();

        var ws = WebServer
            .builder(
                Routing
                    .builder()
                    .register(WebSecurity.create(security))
//                    .get(
//                        "/profile",
//                        WebSecurity.authenticate().rolesAllowed("ROLE_1", "TEST"),
//                        (rq, rs) -> {
//                            rq.context().get(SecurityContext.class).ifPresentOrElse(
//                                ctx -> ctx.atnClientBuilder().submit().whenComplete((result, error) -> {
//                                    if (error != null) {
//                                        rs.status(Http.Status.UNAUTHORIZED_401).send();
//                                    }
//                                    else {
//                                        var user = result.user().get();
//                                        var response = new HashMap<String, Object>();
//                                        response.put("subject", user.principal().getName());
//                                        response.put("roles", user.grants(Role.class));
//                                        rs.status(Http.Status.OK_200).send(response);
//                                    }
//                                }),
//                                () -> rs.status(Http.Status.INTERNAL_SERVER_ERROR_500).send()
//                            );
//                        }
//                    )
                    .any(new TokenEndpoint(appConfig, keySet), new StubHandler(appConfig))
                    .build()
            )
            .config(config.get("server"))
            .addMediaSupport(JacksonSupport.create())
            .build()
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
