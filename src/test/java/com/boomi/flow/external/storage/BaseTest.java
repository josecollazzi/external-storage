package com.boomi.flow.external.storage;

import com.boomi.flow.external.storage.utils.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manywho.sdk.api.jackson.ObjectMapperFactory;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jboss.resteasy.util.PortProvider;
import org.jdbi.v3.core.Jdbi;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

import static org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers.AES_192_CBC_HMAC_SHA_384;
import static org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers.AES_256_CBC_HMAC_SHA_512;
import static org.jose4j.jwe.KeyManagementAlgorithmIdentifiers.ECDH_ES_A192KW;
import static org.jose4j.jwe.KeyManagementAlgorithmIdentifiers.ECDH_ES_A256KW;
import static org.jose4j.jws.AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384;

public class BaseTest {
    protected static ObjectMapper objectMapper = ObjectMapperFactory.create();
    protected static UndertowJaxrsServer server;

    @BeforeAll
    public static void startServer() {
        server = new UndertowJaxrsServer();
        server.start();
        server.deploy(ApplicationTest.class);
    }

    @AfterAll
    public static void stopServer() {
        server.stop();
    }

    public Jdbi createJdbi() {
        return Jdbi.create(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"));
    }

    protected static String createRequestSignature(UUID tenant, String endpoint) throws JoseException {
        URL url;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new RuntimeException("An invalid URL was given", e);
        }

        // Create the claims, which will be the content of the JWT
        JwtClaims claims = new JwtClaims();
        claims.setIssuer("manywho");  // who creates the token and signs it
        claims.setAudience(tenant.toString()); // to whom the token is intended to be sent
        claims.setExpirationTimeMinutesInTheFuture(5); // time when the token will expire (10 minutes from now)
        claims.setGeneratedJwtId(); // a unique identifier for the token
        claims.setIssuedAtToNow();  // when the token was issued/created (now)
        claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
        claims.setSubject(tenant.toString()); // the subject/principal is whom the token is about

        // Add the state metadata as claims, so any verifying consumer gets undeniably correct information
        claims.setClaim("path", url.getPath());

        // Create the signature for the actual content
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384); // TODO: Check this

        PublicJsonWebKey platformFull = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));

        jws.setKey(platformFull.getPrivateKey());
        jws.setKeyIdHeaderValue(platformFull.getKeyId());
        jws.setPayload(claims.toJson());

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException("Unable to serialize the JWS", e);
        }
    }

    protected static String decryptToken(String token) throws JoseException, InvalidJwtException, MalformedClaimException {
        PublicJsonWebKey plaformFull = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
        PublicJsonWebKey receiverFull = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("RECEIVER_KEY"));

        // Create constraints for the algorithms that incoming tokens need to use, otherwise decoding will fail
        var jwsAlgorithmConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, ECDSA_USING_P384_CURVE_AND_SHA384);
        var jweAlgorithmConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, ECDH_ES_A192KW, ECDH_ES_A256KW);
        var jceAlgorithmConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.WHITELIST, AES_192_CBC_HMAC_SHA_384, AES_256_CBC_HMAC_SHA_512);

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setMaxFutureValidityInMinutes(300)
                .setExpectedIssuer("receiver")
                .setExpectedAudience("manywho")
                .setDecryptionKey(plaformFull.getPrivateKey())
                .setVerificationKey(receiverFull.getPublicKey())
                .setJwsAlgorithmConstraints(jwsAlgorithmConstraints)
                .setJweAlgorithmConstraints(jweAlgorithmConstraints)
                .setJweContentEncryptionAlgorithmConstraints(jceAlgorithmConstraints)
                .build();

        JwtClaims claims;
        claims = jwtConsumer.processToClaims(token);

        return claims.getStringClaimValue("content");
    }

    protected static String testUrl(String path) {
        return String.format("http://%s:%d%s", PortProvider.getHost(), PortProvider.getPort(), path);
    }

    public static String databaseType() {
        return URI.create(Environment.get("DATABASE_URL").trim().substring(5)).getScheme();
    }
}
