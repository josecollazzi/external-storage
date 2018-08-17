create table states
(
  id CHAR(36) not null primary key,
  tenant_id CHAR(36) not null,
  parent_id CHAR(36),
  flow_id CHAR(36) not null,
  flow_version_id CHAR(36) not null,
  is_done boolean not null,
  current_map_element_id CHAR(36) not null,
  current_user_id CHAR(36),
  created_at datetime(6) not null,
  updated_at datetime(6) not null,
  content JSON not null
);
