package com.sschakraborty.poc.log4shell;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        vertx.createHttpServer().requestHandler(request -> {

            // Set a ThreadContext variable "hostName"
            ThreadContext.put("hostName", request.host());

            // Invoke logger
            logger.info("Received a connection!");

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
