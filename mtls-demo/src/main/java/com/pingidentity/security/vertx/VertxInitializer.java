package com.pingidentity.security.vertx;

import com.pingidentity.security.Client;
import com.pingidentity.security.Server;
import com.pingidentity.security.vertx.mtls.MutualTLSVertxClient;
import com.pingidentity.security.vertx.mtls.MutualTLSVertxServer;
import com.pingidentity.security.vertx.plaintext.PlaintextVertxServer;
import com.pingidentity.security.vertx.tls.TLSVertxServer;
import io.vertx.core.Vertx;

public class VertxInitializer {
    public static void main(String[] args) {
        final Vertx vertx = Vertx.vertx();
        final Server plaintextVertxServer = new PlaintextVertxServer(vertx);
        final Server tlsVertxServer = new TLSVertxServer(vertx);
        final Server mutualTLSVertxServer = new MutualTLSVertxServer(vertx);
        final Client mutualTLSVertxClient = new MutualTLSVertxClient(vertx);
        try {
            plaintextVertxServer.spawn();
            tlsVertxServer.spawn();
            mutualTLSVertxServer.spawn();
            vertx.setTimer(2000, context -> {
                mutualTLSVertxClient.connect();
            });
        } catch (Throwable throwable) {
            System.out.println(throwable);
        }
    }
}
