package com.boomi.flow.external.storage.states;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;

public class SqlServerDatabaseRepository extends StateDatabaseRepository {

    public SqlServerDatabaseRepository(Jdbi jdbi)
    {
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
                .bind("createdAt", state.getCreatedAt().toString())
                .bind("updatedAt", state.getUpdatedAt().toString())
                .bind("expiresAt", state.getExpiresAt().toString())
                .add();
    }

    @Override
    protected String upsertQuery() {
        return ("MERGE states WITH (HOLDLOCK) AS myTarget " +
                "  USING (SELECT :id id, :tenant tenant_id, :parent parent_id, :flow flow_id, :flowVersion flow_version_id, :isDone is_done, :currentMapElement current_map_element_id, :currentUser current_user_id, :createdAt created_at, :updatedAt updated_at, :expiresAt expires_at, :content content) AS mySource " +
                "    ON mySource.id = myTarget.id  and mySource.id=:id " +
                "WHEN MATCHED and myTarget.updated_at <= mySource.updated_at THEN UPDATE " +
                "    SET flow_id =mySource.flow_id, flow_version_id =mySource.flow_version_id, is_done =mySource.is_done, current_map_element_id=mySource.current_map_element_id, current_user_id = mySource.current_user_id, updated_at =mySource.updated_at, expires_at=mySource.expires_at, content=mySource.content " +
                "WHEN NOT MATCHED THEN " +
                "    INSERT (id, tenant_id, parent_id, flow_id, flow_version_id, is_done, current_map_element_id, current_user_id, created_at, updated_at, expires_at, content) " +
                "    VALUES (mySource.id, mySource.tenant_id, mySource.parent_id, mySource.flow_id, mySource.flow_version_id, mySource.is_done, mySource.current_map_element_id, mySource.current_user_id, mySource.created_at, mySource.updated_at, mySource.expires_at, mySource.content); ");

    }
}
