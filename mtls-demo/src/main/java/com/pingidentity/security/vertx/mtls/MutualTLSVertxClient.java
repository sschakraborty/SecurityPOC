package com.pingidentity.security.vertx.mtls;

import com.pingidentity.security.Client;
import io.vertx.core.Vertx;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.Objects;

public class MutualTLSVertxClient implements Client {
    private final Vertx vertx;
    private final String keyStorePath;
    private final String trustStorePath;

    public MutualTLSVertxClient(Vertx vertx) {
        this.vertx = vertx;
        this.keyStorePath = Objects.requireNonNull(getClass().getClassLoader().getResource("TLSClientKeystore.jks")).getPath();
        this.trustStorePath = Objects.requireNonNull(getClass().getClassLoader().getResource("TLSClientTruststore.jks")).getPath();
    }

    @Override
    public void connect() {
        final WebClientOptions options = new WebClientOptions().setUserAgent("InternalMutualTLSClient/0.0.1").setKeepAlive(false);
        options.setKeyStoreOptions(new JksOptions().setPath(keyStorePath).setPassword("2Federate!"));
        options.setTrustStoreOptions(new JksOptions().setPath(trustStorePath).setPassword("2Federate!"));
        options.setSsl(true).setVerifyHost(true);
        final WebClient webClient = WebClient.create(vertx, options);

        webClient.get(9500, "localhost", "/").send().onSuccess(response -> {
            System.out.println(response.bodyAsString());
        });
    }
}
