package com.pingidentity.security.vertx.plaintext;

import com.pingidentity.security.Server;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class PlaintextVertxServer implements Server {
    private final Vertx vertx;

    public PlaintextVertxServer(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void spawn() {
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);

        router.route().handler(routingContext -> {
            routingContext.end("Hello World from Vertx Plaintext Server!");
        });

        httpServer.requestHandler(router).listen(9000);
    }
}
