alter table projects add column if not exists description_de varchar(3000);
alter table projects add column if not exists description_en varchar(3000);

update projects
set description_en = description
where description_en is null;

alter table projects drop constraint if exists project_description_length_check;
alter table projects drop column description;

alter table projects add constraint project_description_de_length_check check (
    description_de is null or length(description_de) between 40 and 3000
);
alter table projects add constraint project_description_en_length_check check (
    description_en is null or length(description_en) between 40 and 3000
);
alter table projects add constraint project_description_localized_present_check check (
    description_de is not null or description_en is not null
);
