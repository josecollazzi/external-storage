package com.boomi.flow.external.storage.states;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;

public class PostgresqlStateDatabaseRepository extends StateDatabaseRepository {

    public PostgresqlStateDatabaseRepository(Jdbi jdbi) {
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
                .bind("createdAt", state.getCreatedAt())
                .bind("updatedAt", state.getUpdatedAt())
                .bind("expiresAt", state.getExpiresAt())
                .add();
    }

    @Override
    protected String upsertQuery() {
        return "INSERT INTO states (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) VALUES (:id, :tenant, :parent, :flow, :flowVersion, :isDone, :currentMapElement, :currentUser, :createdAt, :updatedAt, :expiresAt, :content::jsonb) ON CONFLICT (id) DO UPDATE SET (flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, updated_at, expires_at, content) = (:flow, :flowVersion, :isDone, :currentMapElement, :currentUser, :updatedAt, :expiresAt, :content::jsonb) WHERE states.id = :id AND states.updated_at <= :updatedAt";
    }
}
