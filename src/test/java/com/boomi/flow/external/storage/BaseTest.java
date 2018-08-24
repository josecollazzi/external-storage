package com.boomi.flow.external.storage;

import com.boomi.flow.external.storage.guice.HikariDataSourceProvider;
import com.boomi.flow.external.storage.guice.StateRepositoryProvider;
import com.boomi.flow.external.storage.keys.KeyRepository;
import com.boomi.flow.external.storage.keys.KeyRepositoryProvider;
import com.boomi.flow.external.storage.states.StateRepository;
import com.boomi.flow.external.storage.utils.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.manywho.sdk.api.jackson.ObjectMapperFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;

import static org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers.AES_192_CBC_HMAC_SHA_384;
import static org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers.AES_256_CBC_HMAC_SHA_512;
import static org.jose4j.jwe.KeyManagementAlgorithmIdentifiers.ECDH_ES_A192KW;
import static org.jose4j.jwe.KeyManagementAlgorithmIdentifiers.ECDH_ES_A256KW;
import static org.jose4j.jws.AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384;
import static org.mockito.Mockito.mock;

public class BaseTest {
    protected static ObjectMapper objectMapper = ObjectMapperFactory.create();

    protected HikariDataSource dataSource(String schema) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPassword(Environment.get("DATABASE_PASSWORD"));
        hikariConfig.setUsername(Environment.get("DATABASE_USERNAME"));
        hikariConfig.setJdbcUrl(Environment.get("DATABASE_URL"));
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setSchema(schema);

        return new HikariDataSource(hikariConfig);
    }

    protected void createSchema(String schema) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPassword(Environment.get("DATABASE_PASSWORD"));
        hikariConfig.setUsername(Environment.get("DATABASE_USERNAME"));
        hikariConfig.setJdbcUrl(Environment.get("DATABASE_URL"));
        hikariConfig.setMaximumPoolSize(1);

        HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
        Jdbi jdbi = Jdbi.create(hikariDataSource);
        jdbi.useHandle(handle -> handle.execute("CREATE SCHEMA " + schema));
    }

    protected void deleteSchema(String schema) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPassword(Environment.get("DATABASE_PASSWORD"));
        hikariConfig.setUsername(Environment.get("DATABASE_USERNAME"));
        hikariConfig.setJdbcUrl(Environment.get("DATABASE_URL"));
        hikariConfig.setMaximumPoolSize(1);

        HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
        Jdbi jdbi = Jdbi.create(hikariDataSource);
        try {
            if ("postgresql".equals(databaseType())) {
                jdbi.useHandle(handle -> handle.execute("DROP SCHEMA " + schema + " CASCADE"));
            } else {
                jdbi.useHandle(handle -> handle.execute("DROP SCHEMA " + schema ));
            }
        }catch (Exception e){}
    }

    protected String attachRandomString(String schema) {
        return schema + "_" + UUID.randomUUID().toString().replace("-","");
    }

    protected StoppableUndertowServer startServer(Jdbi jdbi) {
        var dataSource = new HikariDataSourceProvider().get();
        Migrator.executeMigrations(dataSource);

        StoppableUndertowServer server = new StoppableUndertowServer();
        server.addModule(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Jdbi.class).toInstance(jdbi);
                bind(KeyRepository.class).toProvider(KeyRepositoryProvider.class).in(Singleton.class);
                bind(StateRepository.class).toProvider(StateRepositoryProvider.class).in(Singleton.class);
            }
        });
        server.setApplication(Application.class);
        try {
            server.start("/api/storage/1", 8080);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return server;
    }

    /**
     * start the server without database speed up the tests
     */
    protected StoppableUndertowServer startServer() {
        Jdbi mockedJdi = mock(Jdbi.class);
        StoppableUndertowServer server = new StoppableUndertowServer();
        server.addModule(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Jdbi.class).toInstance(mockedJdi);
                bind(KeyRepository.class).toProvider(KeyRepositoryProvider.class).in(Singleton.class);
                bind(StateRepository.class).toProvider(StateRepositoryProvider.class).in(Singleton.class);
            }
        });
        server.setApplication(Application.class);

        try {
            server.start("/api/storage/1", 8080);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return server;
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
        PublicJsonWebKey platformFull = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
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
                .setDecryptionKey(platformFull.getPrivateKey())
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
        return String.format("http://%s:%d/api/storage/1%s", PortProvider.getHost(), 8080, path);
    }

    public static String databaseType() {
        return URI.create(Environment.get("DATABASE_URL").trim().substring(5)).getScheme();
    }
}
