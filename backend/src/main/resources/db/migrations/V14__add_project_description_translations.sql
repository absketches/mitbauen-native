create table if not exists project_description_translations (
    project_id bigint not null references projects (id) on delete cascade,
    source_language varchar(8) not null,
    target_language varchar(8) not null,
    source_text_hash varchar(64) not null,
    translated_text varchar(3000) not null,
    provider varchar(40) not null,
    model varchar(120) not null,
    created_at timestamp not null default current_timestamp,
    primary key (project_id, source_language, target_language),
    constraint project_description_translation_languages_check check (source_language <> target_language),
    constraint project_description_translation_target_check check (target_language in ('de', 'en')),
    constraint project_description_translation_source_check check (source_language in ('de', 'en')),
    constraint project_description_translation_text_length_check check (length(translated_text) between 1 and 3000)
);
