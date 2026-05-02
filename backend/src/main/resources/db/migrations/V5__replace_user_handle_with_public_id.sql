alter table users add column if not exists public_id varchar(80);

update users
set public_id = handle
where public_id is null;

alter table users alter column public_id set not null;
create unique index if not exists users_public_id_unique on users (public_id);

alter table users drop column if exists handle;
