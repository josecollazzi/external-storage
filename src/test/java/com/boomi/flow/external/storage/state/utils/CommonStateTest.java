package com.boomi.flow.external.storage.state.utils;

import com.boomi.flow.external.storage.BaseTest;
import org.jdbi.v3.core.Jdbi;

public class CommonStateTest {
    public static String insertState() {

        switch (BaseTest.databaseType()) {
            case "mysql":
                return "INSERT INTO states (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) VALUES " +
                        "('4b8b27d3-e4f3-4a78-8822-12476582af8a', '918f5a24-290e-4659-9cd6-c8d95aee92c6', null, '7808267e-b09a-44b2-be2b-4216a9513b71', 'f8bfd40b-8e0b-4966-884e-ed6159aec3dc', 1, '6dc7aea2-335d-40d0-ba34-4571fb135936', '52df1a90-3826-4508-b7c2-cde8aa5b72cf', '2018-07-17 11:32:00.7117030', '2018-07-17 11:32:00.7117030', '2018-07-17T11:32:00.711703', :content)";
            case "sqlserver":
                return "INSERT INTO states (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) VALUES " +
                        "('4b8b27d3-e4f3-4a78-8822-12476582af8a', '918f5a24-290e-4659-9cd6-c8d95aee92c6', null, '7808267e-b09a-44b2-be2b-4216a9513b71', 'f8bfd40b-8e0b-4966-884e-ed6159aec3dc', 1, '6dc7aea2-335d-40d0-ba34-4571fb135936', '52df1a90-3826-4508-b7c2-cde8aa5b72cf', '2018-07-17 11:32:00.7117030 +01:00', '2018-07-17 11:32:00.7117030 +01:00', '2018-07-17T11:32:00.711703+01:00', :content)";
            default:
                return "INSERT INTO states (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) VALUES " +
                        "('4b8b27d3-e4f3-4a78-8822-12476582af8a', '918f5a24-290e-4659-9cd6-c8d95aee92c6', null, '7808267e-b09a-44b2-be2b-4216a9513b71', 'f8bfd40b-8e0b-4966-884e-ed6159aec3dc', true, '6dc7aea2-335d-40d0-ba34-4571fb135936', '52df1a90-3826-4508-b7c2-cde8aa5b72cf', '2018-07-17 11:32:00.7117030 +01:00', '2018-07-17 11:32:00.7117030 +01:00', '2018-07-17T11:32:00.711703+01:00', :content::jsonb)";
        }
    }

    public static void cleanSates(Jdbi jdbi) {

        String sqlDelete = "DELETE FROM states";
        jdbi.useHandle(handle -> {
            handle.createUpdate(sqlDelete)
                    .execute();
        });
    }
}
