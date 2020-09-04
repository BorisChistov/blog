package com.borischistov;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class TokenEndpoint implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(TokenEndpoint.class);

    private TokenEndpointConfig tokenEndpointConfig;
    private final JwkKeys keys;

    public TokenEndpoint(Config appConfig, JwkKeys keys) {
        this.keys = keys;
        this.tokenEndpointConfig = new TokenEndpoint.TokenEndpointConfig(appConfig.get("token"));
        appConfig.onChange(newConfig -> {
            logger.debug("Config updated");
            this.tokenEndpointConfig = new TokenEndpoint.TokenEndpointConfig(newConfig.get("token"));
        });
    }

    @Override
    public void accept(final ServerRequest rq, final ServerResponse rs) {
        var conf = this.tokenEndpointConfig;
        if (rq.path().toRawString().equals(conf.path)) {
            if (rq.method().equals(conf.method)) {
                var jwtBuilder = Jwt
                    .builder()
                    .keyId(conf.keyId)
                    .subject(conf.subject)
                    .notBefore(Instant.now())
                    .expirationTime(Instant.now().plus(conf.ttl));
                conf.roles.forEach(jwtBuilder::addUserGroup);
                var signed = SignedJwt.sign(jwtBuilder.build(), keys);
                rs.status(Http.Status.OK_200).send(signed.tokenContent());
                return;
            }
        }
        rq.next();
    }

    @Getter
    private class TokenEndpointConfig {
        private final Http.Method method;
        private final String path;
        private final String keyId;
        private final String subject;
        private final List<String> roles;
        private final Duration ttl;

        public TokenEndpointConfig(Config config) {
            this.method = config.get("method").asString().map(Http.Method::valueOf).orElse(Http.Method.GET);
            this.path = config.get("path").asString().orElse("/token");
            this.keyId = config.get("key-id").asString().get();
            this.subject = config.get("subject").asString().orElse("user");
            this.roles = config.get("roles").asList(String.class).orElse(Collections.emptyList());
            this.ttl = config.get("ttl").asString().map(Duration::parse).orElse(Duration.ofHours(1));
        }
    }
}
