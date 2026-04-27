alter table invite_links add column if not exists token_hash varchar(64);
create unique index if not exists invite_links_token_hash_unique on invite_links (token_hash);
alter table invite_links alter column token drop not null;
