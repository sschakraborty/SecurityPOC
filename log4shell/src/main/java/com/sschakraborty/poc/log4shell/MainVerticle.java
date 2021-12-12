package com.sschakraborty.poc.log4shell;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        vertx.createHttpServer().requestHandler(request -> {
            logger.info("Received request with host: {}", request.host());
            request.response().putHeader("content-type", "text/plain").end("Hello World!");
        }).listen(8888, http -> {
            if (http.succeeded()) {
                logger.info("HTTP server started on port 8888");
                startPromise.complete();
            } else {
                logger.error("HTTP server started on port 8888");
                startPromise.fail(http.cause());
            }
        });
    }
}
