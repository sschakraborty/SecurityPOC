package com.pingidentity.security.vertx.mtls;

import com.pingidentity.security.Client;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;

public class MutualTLSApacheHTTPClient implements Client {
    @Override
    public void connect() {
        try (final CloseableHttpClient httpClient = createHttpClient()) {
            final HttpGet httpGet = new HttpGet("https://localhost:9500/");
            final HttpResponse httpResponse = httpClient.execute(httpGet);
            System.out.println("Apache HTTP Client (mTLS): " + new String(httpResponse.getEntity().getContent().readAllBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CloseableHttpClient createHttpClient() throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException {
        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        clientBuilder.setSSLContext(createSSLContext()).setUserAgent("Mandelbrot");
        return clientBuilder.build();
    }

    private SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException, CertificateException, KeyStoreException, IOException, UnrecoverableKeyException {
        final SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
        final File trustStoreFile = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("TLSClientTruststore.jks")).getPath());
        final File keyStoreFile = new File(Objects.requireNonNull(getClass().getClassLoader().getResource("TLSClientKeystore.jks")).getPath());
        sslContextBuilder.loadTrustMaterial(trustStoreFile, "2Federate!".toCharArray());
        sslContextBuilder.loadKeyMaterial(keyStoreFile, "2Federate!".toCharArray(), "2Federate!".toCharArray());
        return sslContextBuilder.build();
    }
}
