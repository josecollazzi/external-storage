package com.boomi.flow.external.storage.states;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;

import java.time.ZoneOffset;

public class MySqlStateDatabaseRepository extends StateDatabaseRepository {

    public MySqlStateDatabaseRepository(Jdbi jdbi) {
        super(jdbi);
    }

    @Override
    protected void addStateToBatch(PreparedBatch batch, State state) {
        batch.bind("id", state.getId())
                .bind("tenant", state.getTenantId())
                .bind("parent", state.getParentId())
                .bind("flow", state.getFlowId())
                .bind("flowVersion", state.getFlowVersionId())
                .bind("isDone", state.isDone())
                .bind("currentMapElement", state.getCurrentMapElementId())
                .bind("currentUser", state.getCurrentUserId())
                .bind("content", state.getContent())
                .bind("createdAt", state.getCreatedAt().atZoneSameInstant(ZoneOffset.UTC))
                .bind("updatedAt", state.getUpdatedAt().atZoneSameInstant(ZoneOffset.UTC))
                .bind("expiresAt", state.getExpiresAt().atZoneSameInstant(ZoneOffset.UTC))
                .add();
    }

    @Override
    protected String upsertQuery() {
        // "on duplicate key update" with a WHERE expression is not supported in MySql
        // the column updated_at needs to be the last updated, in other case some fields can not be updated

        return "INSERT INTO states (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) " +
                    "VALUES (:id, :tenant, :parent, :flow, :flowVersion, :isDone, :currentMapElement, :currentUser, :createdAt, :updatedAt, :expiresAt, :content) " +
                "ON DUPLICATE KEY UPDATE " +
                    "flow_id = IF(updated_at < VALUES(updated_at), VALUES(flow_id), flow_id)," +
                    "flow_version_id = IF(updated_at < VALUES(updated_at), VALUES(flow_version_id), flow_version_id)," +
                    "is_done = IF(updated_at < VALUES(updated_at), VALUES(is_done), is_done)," +
                    "current_map_element_id = IF(updated_at < VALUES(updated_at), VALUES(current_map_element_id), current_map_element_id)," +
                    "current_user_id= IF(updated_at < VALUES(updated_at), VALUES(current_user_id), current_user_id)," +
                    "content= IF(updated_at < VALUES(updated_at), VALUES(content), content)," +
                    "expires_at= IF(updated_at < VALUES(updated_at), VALUES(expires_at), expires_at)," +
                    "updated_at= IF(updated_at < VALUES(updated_at), VALUES(updated_at), updated_at)";
    }
}
