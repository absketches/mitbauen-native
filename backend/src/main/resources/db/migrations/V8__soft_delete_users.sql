alter table users add column if not exists is_deleted boolean not null default false;
alter table users add column if not exists deleted_at timestamp;

create index if not exists users_public_id_deleted_idx on users (public_id, is_deleted);
