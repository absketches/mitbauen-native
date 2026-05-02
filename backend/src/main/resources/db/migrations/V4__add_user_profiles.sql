alter table users add column if not exists bio varchar(560) not null default '';
alter table users add column if not exists is_email_public boolean not null default false;
