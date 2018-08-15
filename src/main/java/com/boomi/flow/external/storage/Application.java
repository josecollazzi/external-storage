package com.boomi.flow.external.storage;

import com.boomi.flow.external.storage.guice.HikariDataSourceProvider;
import com.manywho.sdk.services.servers.EmbeddedServer;
import com.manywho.sdk.services.servers.undertow.UndertowServer;


public class Application {
    public static void main(String[] args) throws Exception {
        var dataSource = new HikariDataSourceProvider().get();
        Migrator.executeMigrations(dataSource);

        EmbeddedServer server = new UndertowServer();
        server.addModule(new ApplicationModule());
        server.setApplication(Application.class);
        server.start("/api/storage/1");
    }
}
