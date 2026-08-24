create index if not exists project_roles_project_founder_idx
    on project_roles (project_id, is_founder);

create index if not exists project_roles_project_open_sort_idx
    on project_roles (project_id, is_open, is_founder, sort_order, id);

create index if not exists project_roles_open_project_sort_idx
    on project_roles (is_open, is_founder, project_id, sort_order, id);
