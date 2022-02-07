package com.pingidentity.security.poc.rce.jndi_ldap_rce;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.io.IOException;
import java.util.logging.Logger;

public class MainVerticle extends AbstractVerticle {
    private final Logger logger = Logger.getLogger(MainVerticle.class.getName());
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        final HttpServer server = vertx.createHttpServer();
        final Router router = Router.router(vertx);
        objectMapper.enableDefaultTyping();
        router.route().handler(BodyHandler.create());
        router.route().handler(this::processContext);
        server.requestHandler(router).listen(8888, result -> {
            if (result.succeeded()) {
                logger.info("Deployed server on port 8888!");
                startPromise.complete();
            } else {
                startPromise.fail("Failed to deploy server - " + result.cause());
            }
        });
    }

    private void processContext(RoutingContext routingContext) {
        final MyBean myBean;
        try {
            myBean = objectMapper.readValue(routingContext.getBodyAsString(), MyBean.class);
            routingContext.response().end(myBean.toString());
        } catch (IOException e) {
            routingContext.response().end(e.getMessage());
        }
    }
}
