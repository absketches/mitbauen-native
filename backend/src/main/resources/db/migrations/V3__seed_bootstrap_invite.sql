insert into invite_links (token_hash, is_active, use_count)
select 'db1a9946e523da75835d9237f85b8552d08f84f4e3fe0979fcc6a3e68797efdc', true, 0
where not exists (
    select 1 from invite_links where token_hash = 'db1a9946e523da75835d9237f85b8552d08f84f4e3fe0979fcc6a3e68797efdc'
);
