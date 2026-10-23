package com.example.backend.config;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.InsecureChannelCredentials;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class FabricGatewayConfig {

    @Value("${fabric.msp-id:Org1MSP}")
    private String mspId;

    @Value("${fabric.peer-endpoint:localhost:7051}")
    private String peerEndpoint;

    @Value("${fabric.override-auth:peer0.org1.example.com}")
    private String overrideAuth;

    @Value("${fabric.crypto-path:/home/intel/Projects/Skripsi/delegated-access-control/fabric-network/test-network/organizations}")
    private String cryptoPath;

    @Value("${fabric.tls-cert-path:/home/intel/Projects/Skripsi/delegated-access-control/fabric-network/test-network/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt}")
    private String tlsCertPath;

    @Bean
    public ManagedChannel fabricChannel() {
        log.debug("[FABRIC_CHANNEL_CONFIG]: Initializing gRPC ManagedChannel to Peer '{}'", peerEndpoint);
        try {
            Path tlsCaPath = Paths.get(tlsCertPath);
            if (Files.exists(tlsCaPath)) {
                return Grpc.newChannelBuilder(peerEndpoint, TlsChannelCredentials.newBuilder().trustManager(tlsCaPath.toFile()).build())
                        .overrideAuthority(overrideAuth)
                        .keepAliveTime(30, TimeUnit.SECONDS)
                        .keepAliveTimeout(5, TimeUnit.SECONDS)
                        .build();
            } else {
                return Grpc.newChannelBuilder(peerEndpoint, InsecureChannelCredentials.create())
                        .keepAliveTime(30, TimeUnit.SECONDS)
                        .keepAliveTimeout(5, TimeUnit.SECONDS)
                        .build();
            }
        } catch (Exception e) {
            log.warn("[FABRIC_CHANNEL_CONFIG]: WARN - Failed to initialize gRPC channel: {}", e.getMessage());
            return null;
        }
    }

    @Bean
    public Gateway fabricGateway(ManagedChannel fabricChannel) {
        log.debug("[FABRIC_GATEWAY_CONFIG]: DEBUG - Initializing Fabric Gateway with ManagedChannel");
        if (fabricChannel == null) {
            log.warn("[FABRIC_GATEWAY_CONFIG]: WARN - ManagedChannel is null, skipping Gateway init.");
            return null;
        }

        try {
            Path certPath = Paths.get(cryptoPath, "peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/signcerts/User1@org1.example.com-cert.pem");
            Path keyDirPath = Paths.get(cryptoPath, "peerOrganizations/org1.example.com/users/User1@org1.example.com/msp/keystore");

            if (!Files.exists(certPath)) {
                log.warn("[FABRIC_GATEWAY_CONFIG]: WARN - MSP sign certificate not found at {}. Running in simulation mode.", certPath);
                return null;
            }

            X509Certificate certificate;
            try (Reader certReader = Files.newBufferedReader(certPath)) {
                certificate = Identities.readX509Certificate(certReader);
            }

            PrivateKey privateKey;
            File[] keyFiles = keyDirPath.toFile().listFiles((dir, name) -> name.endsWith("_sk") || name.endsWith(".key") || name.endsWith(".pem") || name.equals("priv_sk"));
            if (keyFiles == null || keyFiles.length == 0) {
                log.warn("[FABRIC_GATEWAY_CONFIG]: WARN - MSP private key not found at {}.", keyDirPath);
                return null;
            }
            try (Reader keyReader = Files.newBufferedReader(keyFiles[0].toPath())) {
                privateKey = Identities.readPrivateKey(keyReader);
            }

            Identity identity = new X509Identity(mspId, certificate);
            Signer signer = Signers.newPrivateKeySigner(privateKey);

            Gateway gateway = Gateway.newInstance()
                    .identity(identity)
                    .signer(signer)
                    .connection(fabricChannel)
                    .evaluateOptions(callOptions -> callOptions.withDeadlineAfter(5, TimeUnit.SECONDS))
                    .endorseOptions(callOptions -> callOptions.withDeadlineAfter(15, TimeUnit.SECONDS))
                    .submitOptions(callOptions -> callOptions.withDeadlineAfter(15, TimeUnit.SECONDS))
                    .commitStatusOptions(callOptions -> callOptions.withDeadlineAfter(60, TimeUnit.SECONDS))
                    .connect();

            log.info("[FABRIC_GATEWAY_CONFIG]: SUCCESS - Fabric Gateway connected successfully to Hyperledger Fabric Network (Peer: '{}')",
                    peerEndpoint);
            return gateway;
        } catch (Exception e) {
            log.warn("[FABRIC_GATEWAY_CONFIG]: WARN - Fabric peer node ({}) is currently unreachable: {}. Fallback simulation state enabled",
                    peerEndpoint, e.getMessage());
            return null;
        }
    }
}
