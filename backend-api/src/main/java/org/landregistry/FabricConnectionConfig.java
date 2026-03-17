package org.landregistry;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

@Configuration
public class FabricConnectionConfig {

    private static final String MSP_ID = "Org1MSP";
    private static final String PEER_ENDPOINT = "localhost:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.landregistry.com";

    // Dynamic paths to the crypto materials in your blockchain-network folder
    private static final Path CRYPTO_PATH = Paths.get("..", "blockchain-network", "crypto-config", "peerOrganizations", "org1.landregistry.com");
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.landregistry.com", "msp", "signcerts"));
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.landregistry.com", "msp", "keystore"));
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers", "peer0.org1.landregistry.com", "tls", "ca.crt"));

    @Bean
    public Gateway fabricGateway() throws Exception {
        ManagedChannel channel = newGrpcConnection();

        Gateway.Builder builder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .connection(channel)
                // Set reasonable timeouts for blockchain operations
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        return builder.connect();
    }

    private ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private Identity newIdentity() throws IOException, CertificateException {
        try (var certStream = Files.list(CERT_DIR_PATH)) {
            Path certPath = certStream.findFirst().orElseThrow(() -> new RuntimeException("Missing certificate file"));
            var certificate = Identities.readX509Certificate(Files.newBufferedReader(certPath));
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        try (var keyStream = Files.list(KEY_DIR_PATH)) {
            Path keyPath = keyStream.findFirst().orElseThrow(() -> new RuntimeException("Missing private key file"));
            var privateKey = Identities.readPrivateKey(Files.newBufferedReader(keyPath));
            return Signers.newPrivateKeySigner(privateKey);
        }
    }
}