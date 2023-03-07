package com.pingidentity.security.vertx;

import com.pingidentity.security.Client;
import com.pingidentity.security.Server;
import com.pingidentity.security.vertx.mtls.MutualTLSApacheHTTPClient;
import com.pingidentity.security.vertx.mtls.MutualTLSVertxClient;
import com.pingidentity.security.vertx.mtls.MutualTLSVertxServer;
import com.pingidentity.security.vertx.plaintext.PlaintextVertxServer;
import com.pingidentity.security.vertx.tls.TLSVertxServer;
import io.vertx.core.Vertx;

public class Initializer {
    public static void main(String[] args) {
        final Vertx vertx = Vertx.vertx();
        final Server plaintextVertxServer = new PlaintextVertxServer(vertx);
        final Server tlsVertxServer = new TLSVertxServer(vertx);
        final Server mutualTLSVertxServer = new MutualTLSVertxServer(vertx);
        final Client mutualTLSVertxClient = new MutualTLSVertxClient(vertx);
        final Client mutualTLSApacheHTTPClient = new MutualTLSApacheHTTPClient();
        try {
            plaintextVertxServer.spawn();
            tlsVertxServer.spawn();
            mutualTLSVertxServer.spawn();
            vertx.setTimer(2000, context -> {
                mutualTLSVertxClient.connect();
            });
            new Thread(() -> {
                try {
                    Thread.sleep(2500);
                    mutualTLSApacheHTTPClient.connect();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        } catch (Throwable throwable) {
            System.out.println(throwable);
        }
    }
}
