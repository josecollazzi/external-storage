package com.boomi.flow.external.storage;

import com.boomi.flow.external.storage.utils.Environment;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.net.URI;

public class Migrator {
    public static void executeMigrations(HikariDataSource dataSource) {
        // Run the migrations, then destroy the single-connection pool
        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);

        // possible supported values for path are mysql, postgresql and sqlserver
        var databaseUrl = System.getenv("JDBC_DATABASE_URL");
        if (databaseUrl == null) {
            databaseUrl = Environment.get("DATABASE_URL");
        }

        String path = URI.create(databaseUrl.trim().substring(5)).getScheme();
        flyway.setLocations(String.format("migrations/%s", path));
        flyway.migrate();

        dataSource.close();
    }
}
