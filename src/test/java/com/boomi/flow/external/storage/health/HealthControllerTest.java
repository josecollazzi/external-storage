package com.boomi.flow.external.storage.health;

import com.boomi.flow.external.storage.BaseTest;
import com.boomi.flow.external.storage.Migrator;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jdbi.v3.core.Jdbi;
import org.junit.Assert;
import org.junit.Test;
import javax.ws.rs.core.Response;

public class HealthControllerTest extends BaseTest {
    @Test
    public void testHealthCheck() {
        String schema = attachRandomString("healthcheck");

        createSchema(schema);
        Migrator.executeMigrations(dataSource(schema));
        var server = startServer(Jdbi.create(dataSource(schema)));

        String url = testUrl("/health");
        Response response = new ResteasyClientBuilder().build()
                .target(url)
                .request()
                .get();
        Assert.assertEquals(200, response.getStatus());
        response.close();

        server.stop();
        deleteSchema(schema);
    }
}
