package com.pingidentity.security.vertx.tls;

import com.pingidentity.security.Server;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TLSVertxServer implements Server {
    private final Vertx vertx;
    private final String keyStorePath;


    public TLSVertxServer(Vertx vertx) {
        this.vertx = vertx;
        this.keyStorePath = Objects.requireNonNull(getClass().getClassLoader().getResource("TLSServerKeystore.jks")).getPath();
    }

    @Override
    public void spawn() {
        final HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setKeyStoreOptions(new JksOptions().setPath(keyStorePath).setPassword("2Federate!"));
        httpServerOptions.setSsl(true).setSslHandshakeTimeout(2).setSslHandshakeTimeoutUnit(TimeUnit.MINUTES);
        httpServerOptions.setEnabledSecureTransportProtocols(Set.of("TLSv1.3", "TLSv1.2"));

        final HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        final Router router = Router.router(vertx);

        router.route().handler(routingContext -> {
            routingContext.end("Hello World from Vertx TLS Server!");
        });

        httpServer.requestHandler(router).listen(9250);
    }
}
