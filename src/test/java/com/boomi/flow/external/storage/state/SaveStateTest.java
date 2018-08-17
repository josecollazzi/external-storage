package com.boomi.flow.external.storage.state;

import com.boomi.flow.external.storage.BaseTest;
import com.boomi.flow.external.storage.Migrator;
import com.boomi.flow.external.storage.jdbi.UuidArgumentFactory;
import com.boomi.flow.external.storage.state.utils.CommonStateTest;
import com.boomi.flow.external.storage.state.utils.StateRequest;
import com.boomi.flow.external.storage.states.State;
import com.google.common.io.Resources;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jdbi.v3.core.Jdbi;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public class SaveStateTest extends BaseTest {
    @Test
    public void testUpdateState() throws URISyntaxException, IOException, JoseException, JSONException {
        String schema = attachRandomString("updatestate");
        createSchema(schema);
        Migrator.executeMigrations(dataSource(schema));
        Jdbi jdbi = Jdbi.create(dataSource(schema));
        var server = startServer(jdbi);

        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime updatedAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(2);

        String oldContent = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/state.json").toURI())));

        //insert an state
        jdbi.useHandle(handle -> handle.createUpdate(CommonStateTest.insertState())
                .bind("content", oldContent)
                .execute());

        String content = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/updated-state.json").toURI())));

        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        UUID stateId = UUID.fromString("4b8b27d3-e4f3-4a78-8822-12476582af8a");
        UUID flowId = UUID.fromString("7808267e-b09a-44b2-be2b-4216a9513b71");
        UUID currentMapElementId = UUID.fromString("6dc7aea2-335d-40d0-ba34-4571fb135936");
        UUID currentUserId = UUID.fromString("52df1a90-3826-4508-b7c2-cde8aa5b72cf");
        UUID flowVersionId = UUID.fromString("f8bfd40b-8e0b-4966-884e-ed6159aec3dc");
        UUID parentId = UUID.fromString("dfcf84e6-85de-11e8-adc0-fa7ae01bbebc");

        // you can find examples of the following keys at test/resources/example-key
        PublicJsonWebKey platformFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
        PublicJsonWebKey receiverFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("RECEIVER_KEY"));

        // encrypt and sign body
        StateRequest[] requestList = createSignedEncryptedBody(stateId, tenantId, parentId, flowId, flowVersionId,
                false, currentMapElementId, currentUserId, createdAt, updatedAt, expiresAt, content, platformFullKey, receiverFullKey);

        String url = testUrl("/states/918f5a24-290e-4659-9cd6-c8d95aee92c6");
        Entity<String> entity = Entity.entity(objectMapper.writeValueAsString(requestList), MediaType.APPLICATION_JSON_TYPE);

        Response response =  new ResteasyClientBuilder().build().target(url).request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", createRequestSignature(tenantId, url))
                .post(entity);

        Assert.assertEquals(204, response.getStatus());

        String sql = "SELECT id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, expires_at, updated_at, content " +
                "FROM states WHERE id = :id AND tenant_id = :tenant";

        Optional<State> stateOptional = jdbi.withHandle(handle -> {
            if (databaseType().equals("mysql")) {
                handle.registerArgument(new UuidArgumentFactory());
            }

            return handle.createQuery(sql)
                    .bind("id", stateId)
                    .bind("tenant", tenantId)
                    .mapToBean(State.class)
                    .findFirst();
        });

        Assert.assertTrue(stateOptional.isPresent());
        Assert.assertEquals(stateId, stateOptional.get().getId());
        Assert.assertEquals(tenantId, stateOptional.get().getTenantId());
        Assert.assertNull(stateOptional.get().getParentId());
        Assert.assertEquals(flowId, stateOptional.get().getFlowId());
        Assert.assertEquals(flowVersionId, stateOptional.get().getFlowVersionId());
        // todo fix
        // Assert.assertTrue(stateOptional.get().isDone());
        Assert.assertEquals(currentMapElementId, stateOptional.get().getCurrentMapElementId());

        // todo createJdbi assertion for createAt too
        Assert.assertEquals(updatedAt, stateOptional.get().getUpdatedAt());
        Assert.assertEquals(expiresAt, stateOptional.get().getExpiresAt());

        JSONAssert.assertEquals(content, stateOptional.get().getContent(), false);
        response.close();

        CommonStateTest.cleanSates(jdbi);
        server.stop();
        deleteSchema(schema);
    }

    /**
     * If there is a more recent state in database so we ignore the request (the updated_at in database is newer)
     * that the updated_at in the request
     *
     */
    @Test
    public void testNotUpdateState() throws URISyntaxException, IOException, JoseException {
        String schema = attachRandomString("updatestate");
        createSchema(schema);
        Migrator.executeMigrations(dataSource(schema));
        Jdbi jdbi = Jdbi.create(dataSource(schema));
        var server = startServer(jdbi);

        OffsetDateTime createdAt = OffsetDateTime.now();
        // the current updated_at in database is '2018-07-17T11:32:00.7117030+01:00'
        OffsetDateTime updatedAt = OffsetDateTime.parse("2017-07-12T11:19:52.2303514+01:00");
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(2);

        String oldContent = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/state.json").toURI())));

        //insert an state
        jdbi.useHandle(handle -> handle.createUpdate(CommonStateTest.insertState())
                .bind("content", oldContent)
                .execute());

        String content = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/updated-state.json").toURI())));

        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        UUID stateId = UUID.fromString("4b8b27d3-e4f3-4a78-8822-12476582af8a");
        UUID flowId = UUID.fromString("7808267e-b09a-44b2-be2b-4216a9513b71");
        UUID currentMapElementId = UUID.fromString("6dc7aea2-335d-40d0-ba34-4571fb135936");
        UUID currentUserId = UUID.fromString("52df1a90-3826-4508-b7c2-cde8aa5b72cf");
        UUID flowVersionId = UUID.fromString("f8bfd40b-8e0b-4966-884e-ed6159aec3dc");
        UUID parentId = UUID.fromString("dfcf84e6-85de-11e8-adc0-fa7ae01bbebc");

        // you can find examples of the following keys at test/resources/example-key
        PublicJsonWebKey platformFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
        PublicJsonWebKey receiverFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("RECEIVER_KEY"));

        // encrypt and sign body
        StateRequest[] requestList = createSignedEncryptedBody(stateId, tenantId, parentId, flowId, flowVersionId,
                false, currentMapElementId, currentUserId, createdAt, updatedAt, expiresAt, content, platformFullKey, receiverFullKey);

        String url = testUrl("/states/918f5a24-290e-4659-9cd6-c8d95aee92c6");
        Entity<String> entity = Entity.entity(objectMapper.writeValueAsString(requestList), MediaType.APPLICATION_JSON_TYPE);

        Response response =  new ResteasyClientBuilder().build().target(url).request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", createRequestSignature(tenantId, url))
                .post(entity);

        Assert.assertEquals(204, response.getStatus());

        String sql = "SELECT id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, expires_at, updated_at, content " +
                "FROM states WHERE id = :id AND tenant_id = :tenant";

        Optional<State> stateOptional = jdbi.withHandle(handle -> {
            if (databaseType().equals("mysql")) {
                handle.registerArgument(new UuidArgumentFactory());
            }

            return handle.createQuery(sql)
                    .bind("id", stateId)
                    .bind("tenant", tenantId)
                    .mapToBean(State.class)
                    .findFirst();
        });

        Assert.assertNotEquals(expiresAt, stateOptional.get().getExpiresAt());
        response.close();

        CommonStateTest.cleanSates(jdbi);
        server.stop();
        deleteSchema(schema);
    }

    @Test
    public void testInsertState() throws URISyntaxException, IOException, JoseException, JSONException {
        String schema = attachRandomString("insertstate");
        createSchema(schema);
        Migrator.executeMigrations(dataSource(schema));
        Jdbi jdbi = Jdbi.create(dataSource(schema));
        var server = startServer(jdbi);

        OffsetDateTime createdAd = OffsetDateTime.now();
        OffsetDateTime updatedAt = OffsetDateTime.now().plusDays(1);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(2);

        String content = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/state.json").toURI())));

        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        UUID stateId = UUID.fromString("4b8b27d3-e4f3-4a78-8822-12476582af8a");
        UUID flowId = UUID.fromString("7808267e-b09a-44b2-be2b-4216a9513b71");
        UUID currentMapElementId = UUID.fromString("6dc7aea2-335d-40d0-ba34-4571fb135936");
        UUID currentUserId = UUID.fromString("52df1a90-3826-4508-b7c2-cde8aa5b72cf");
        UUID flowVersionId = UUID.fromString("f8bfd40b-8e0b-4966-884e-ed6159aec3dc");
        UUID parentId = UUID.fromString("dfcf84e6-85de-11e8-adc0-fa7ae01bbebc");

        // you can find examples of the following keys at test/resources/example-key
        PublicJsonWebKey platformFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
        PublicJsonWebKey receiverFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("RECEIVER_KEY"));

        // encrypt and sign body
        StateRequest[] requestList = createSignedEncryptedBody(stateId, tenantId, parentId, flowId, flowVersionId,
                false, currentMapElementId, currentUserId, createdAd, updatedAt, expiresAt, content,
                platformFullKey, receiverFullKey);

        String url = testUrl("/states/918f5a24-290e-4659-9cd6-c8d95aee92c6");
        Entity<String> entity = Entity.entity(objectMapper.writeValueAsString(requestList), MediaType.APPLICATION_JSON_TYPE);

        Response response =  new ResteasyClientBuilder().build().target(url).request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", createRequestSignature(tenantId, url))
                .post(entity);

        Assert.assertEquals(204, response.getStatus());

        String sql = "SELECT id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content " +
                "FROM states WHERE id = :id AND tenant_id = :tenant";

        Optional<State> stateOptional = jdbi.withHandle(handle -> {
            if (databaseType().equals("mysql")) {
                handle.registerArgument(new UuidArgumentFactory());
            }

            return handle.createQuery(sql)
                    .bind("id", stateId)
                    .bind("tenant", tenantId)
                    .mapToBean(State.class)
                    .findFirst();
        });

        Assert.assertTrue(stateOptional.isPresent());
        Assert.assertEquals(stateId, stateOptional.get().getId());
        Assert.assertEquals(tenantId, stateOptional.get().getTenantId());
        Assert.assertEquals(parentId, stateOptional.get().getParentId());
        Assert.assertEquals(flowId, stateOptional.get().getFlowId());
        Assert.assertEquals(flowVersionId, stateOptional.get().getFlowVersionId());
        // todo fix
        // Assert.assertTrue(stateOptional.get().isDone());
        Assert.assertEquals(currentMapElementId, stateOptional.get().getCurrentMapElementId());
        Assert.assertEquals(createdAd, stateOptional.get().getCreatedAt());
        Assert.assertEquals(updatedAt, stateOptional.get().getUpdatedAt());
        Assert.assertEquals(expiresAt, stateOptional.get().getExpiresAt());

        JSONAssert.assertEquals(content, stateOptional.get().getContent(), false);
        response.close();

        CommonStateTest.cleanSates(jdbi);
        server.stop();
        deleteSchema(schema);
    }

    private StateRequest[] createSignedEncryptedBody(UUID id, UUID tenantId, UUID parentId, UUID flowId, UUID flowVersionId,
                                                     boolean isDone, UUID currentMapElement, UUID currentUserId,
                                                     OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime expiresAt,
                                                     String content, PublicJsonWebKey platformJwk, PublicJsonWebKey receiverJwk) {

        // Create the claims, which will be the content of the JWT
        JwtClaims claims = new JwtClaims();
        claims.setIssuer("manywho");  // who creates the token and signs it
        claims.setAudience(tenantId.toString()); // to whom the token is intended to be sent
        claims.setExpirationTimeMinutesInTheFuture(10); // time when the token will expire (10 minutes from now)
        claims.setGeneratedJwtId(); // a unique identifier for the token
        claims.setIssuedAtToNow();  // when the token was issued/created (now)
        claims.setNotBeforeMinutesInThePast(2); // time before which the token is not yet valid (2 minutes ago)
        claims.setSubject(id.toString()); // the subject/principal is whom the token is about

        // Add the state.json metadata as claims, so any verifying consumer gets undeniably correct information
        claims.setClaim("id", id);
        claims.setClaim("tenant", tenantId);
        claims.setClaim("parent", parentId);
        claims.setClaim("flow", flowId);
        claims.setClaim("flowVersion", flowVersionId);
        claims.setClaim("isDone", isDone);
        claims.setClaim("currentMapElement", currentMapElement);
        claims.setClaim("currentUser", currentUserId);
        claims.setClaim("createdAt", createdAt);
        claims.setClaim("updatedAt", updatedAt);
        claims.setClaim("expiresAt", expiresAt);
        claims.setClaim("content", content);

        // Create the signature for the actual content
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384); // TODO: Check this
        jws.setKey(platformJwk.getPrivateKey());
        jws.setKeyIdHeaderValue(platformJwk.getKeyId());
        jws.setPayload(claims.toJson());

        String signedPayload;
        try {
            signedPayload = jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException("Unable to serialize the JWS", e);
        }

        // The outer shell
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.enableDefaultCompression();
        jwe.setContentTypeHeaderValue("JWT");
        jwe.setPayload(signedPayload);
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.ECDH_ES_A192KW);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_192_CBC_HMAC_SHA_384);
        jwe.setKey(receiverJwk.getKey());
        jwe.setKeyIdHeaderValue(receiverJwk.getKeyId());

        String serializedJwe;
        try {
            serializedJwe = jwe.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException("Unable to serialize the JWE", e);
        }

        // Create the payload that the external store is expecting
        return new StateRequest[]{
                new StateRequest(serializedJwe)
        };
    }

    @Test
    public void testNotValidSignature() throws IOException, JoseException, URISyntaxException {
        var server = startServer();
        String url = testUrl("/states/4b8b27d3-e4f3-4a78-8822-12476582af8a");

        Response response =  new ResteasyClientBuilder().build().target(url)
                .request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", "not valid signature")
                .accept(MediaType.APPLICATION_JSON)
                .post(validEntity());

        Assert.assertEquals(401, response.getStatus());
        response.close();
        server.stop();
    }

    @Test
    public void testEmptySignature() throws JoseException, IOException, URISyntaxException {
        var server = startServer();
        String url = testUrl("/states/4b8b27d3-e4f3-4a78-8822-12476582af8a");

        Response response =  new ResteasyClientBuilder().build().target(url)
                .request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .accept(MediaType.APPLICATION_JSON)
                .post(validEntity());

        Assert.assertEquals(401, response.getStatus());
        response.close();
        server.stop();
    }

    @Test
    public void testNonPlatformKey() throws JoseException, IOException, URISyntaxException {
        var server = startServer();
        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        String url = testUrl("/states/918f5a24-290e-4659-9cd6-c8d95aee92c6");

        Response response =  new ResteasyClientBuilder().build().target(url)
                .request()
                .header("X-ManyWho-Receiver-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", createRequestSignature(tenantId, url))
                .accept(MediaType.APPLICATION_JSON)
                .post(validEntity());

        Assert.assertEquals(400, response.getStatus());
        response.close();
        server.stop();
    }

    @Test
    public void testNonReceiverKey() throws JoseException, IOException, URISyntaxException {
        var server = startServer();
        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        String url = testUrl("/states/918f5a24-290e-4659-9cd6-c8d95aee92c6");

        Response response =  new ResteasyClientBuilder().build().target(url)
                .request()
                .header("X-ManyWho-Platform-Key-ID", "918f5a24-290e-4659-9cd6-c8d95aee92c6")
                .header("X-ManyWho-Signature", createRequestSignature(tenantId, url))
                .accept(MediaType.APPLICATION_JSON)
                .post(validEntity());

        Assert.assertEquals(400, response.getStatus());
        response.close();
        server.stop();
    }

    private Entity<String> validEntity() throws IOException, JoseException, URISyntaxException {
        String content = new String(Files.readAllBytes(Paths.get(Resources.getResource("state/state.json").toURI())));

        UUID tenantId = UUID.fromString("918f5a24-290e-4659-9cd6-c8d95aee92c6");
        UUID stateId = UUID.fromString("4b8b27d3-e4f3-4a78-8822-12476582af8a");
        UUID flowId = UUID.fromString("7808267e-b09a-44b2-be2b-4216a9513b71");
        UUID currentMapElementId = UUID.fromString("6dc7aea2-335d-40d0-ba34-4571fb135936");
        UUID currentUserId = UUID.fromString("52df1a90-3826-4508-b7c2-cde8aa5b72cf");
        UUID flowVersionId = UUID.fromString("f8bfd40b-8e0b-4966-884e-ed6159aec3dc");
        UUID parentId = UUID.fromString("dfcf84e6-85de-11e8-adc0-fa7ae01bbebc");

        OffsetDateTime now = OffsetDateTime.now();

        // you can find examples of the following keys at test/resources/example-key
        PublicJsonWebKey platformFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("PLATFORM_KEY"));
        PublicJsonWebKey receiverFullKey = PublicJsonWebKey.Factory.newPublicJwk(System.getenv("RECEIVER_KEY"));

        // encrypt and sign body
        StateRequest[] requestList = createSignedEncryptedBody(stateId, tenantId, parentId, flowId, flowVersionId,
                false, currentMapElementId, currentUserId, now, now, now, content, platformFullKey, receiverFullKey);
        return Entity.entity(objectMapper.writeValueAsString(requestList), MediaType.APPLICATION_JSON_TYPE);
    }
}
