alter table projects drop constraint if exists project_description_length_check;
alter table projects alter column description type varchar(3000);
alter table projects add constraint project_description_length_check check (length(description) between 40 and 3000);

alter table project_roles drop constraint if exists project_role_title_length_check;
alter table project_roles drop constraint if exists project_role_commitment_length_check;
alter table project_roles alter column title type varchar(120);
alter table project_roles alter column commitment type varchar(500);
alter table project_roles add constraint project_role_title_length_check check (length(title) between 3 and 120);
alter table project_roles add constraint project_role_commitment_length_check check (
    (is_founder = true and length(commitment) between 5 and 500)
    or
    (is_founder = false and length(commitment) between 3 and 500)
);
