#!/bin/bash

# Setup workspace
# shellcheck disable=SC2038
find . -not -name '*.sh' -type f | xargs rm -f
PASSWORD='2Federate!'

# Create Server & Client Key and Certificate
CERT_SUBJECT='/C=IN/ST=Karnataka/L=Bangalore/O=Ping Identity/OU=Security/CN=VertxTLSServer'
openssl req -x509 -newkey rsa:4096 -keyout tlsServerKey.pem -out tlsServerCert.pem -sha256 -days 30 -passout "pass:$PASSWORD" -subj "$CERT_SUBJECT" -addext "subjectAltName=DNS:localhost"
CERT_SUBJECT='/C=IN/ST=Karnataka/L=Bangalore/O=Ping Identity/OU=Security/CN=VertxTLSClient'
openssl req -x509 -newkey rsa:4096 -keyout tlsClientKey.pem -out tlsClientCert.pem -sha256 -days 30 -passout "pass:$PASSWORD" -subj "$CERT_SUBJECT" -addext "subjectAltName=DNS:localhost"

# Create Server KeyStore and TrustStore
KEY_STORE_NAME='TLSServerKeystore'
TRUST_STORE_NAME='TLSServerTruststore'
openssl pkcs12 -export -out "$KEY_STORE_NAME.int.p12" -inkey tlsServerKey.pem -in tlsServerCert.pem -name rootCert -passout "pass:$PASSWORD" -passin "pass:$PASSWORD"
keytool -importkeystore -destkeystore "$KEY_STORE_NAME.jks" -srckeystore "$KEY_STORE_NAME.int.p12" -srcstoretype PKCS12 -alias rootCert -srcstorepass "$PASSWORD" -deststorepass "$PASSWORD" -destkeypass "$PASSWORD"
keytool -import -alias rootClientCert -file tlsClientCert.pem -keystore "$TRUST_STORE_NAME.jks" -storepass "$PASSWORD" -noprompt

# Create Client KeyStore and TrustStore
KEY_STORE_NAME='TLSClientKeystore'
TRUST_STORE_NAME='TLSClientTruststore'
openssl pkcs12 -export -out "$KEY_STORE_NAME.int.p12" -inkey tlsClientKey.pem -in tlsClientCert.pem -name rootClientCert -passout "pass:$PASSWORD" -passin "pass:$PASSWORD"
keytool -importkeystore -destkeystore "$KEY_STORE_NAME.jks" -srckeystore "$KEY_STORE_NAME.int.p12" -srcstoretype PKCS12 -alias rootClientCert -srcstorepass "$PASSWORD" -deststorepass "$PASSWORD" -destkeypass "$PASSWORD"
keytool -import -alias rootCert -file tlsServerCert.pem -keystore "$TRUST_STORE_NAME.jks" -storepass "$PASSWORD" -noprompt

# Clean up
find . -name '*.pem' -type f | xargs rm -f
find . -name '*.p12' -type f | xargs rm -f
