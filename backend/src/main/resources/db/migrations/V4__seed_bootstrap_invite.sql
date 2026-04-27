insert into invite_links (token_hash, allowed_email, is_active, use_count)
select '2243359140661f77f0a1a7de5e90311a08758d0f59066185c938861f6d0eeccf', 'basuabhi92@gmail.com', true, 0
where not exists (
    select 1 from invite_links where token_hash = '2243359140661f77f0a1a7de5e90311a08758d0f59066185c938861f6d0eeccf'
);
