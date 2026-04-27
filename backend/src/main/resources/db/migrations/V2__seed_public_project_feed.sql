insert into users (id, handle, display_name)
select 1, 'avery', 'Avery Bloom'
where not exists (select 1 from users where id = 1);

insert into users (id, handle, display_name)
select 2, 'nora', 'Nora Patel'
where not exists (select 1 from users where id = 2);

insert into users (id, handle, display_name)
select 3, 'elliot', 'Elliot Kim'
where not exists (select 1 from users where id = 3);

insert into users (id, handle, display_name)
select 4, 'mika', 'Mika Rossi'
where not exists (select 1 from users where id = 4);

insert into projects (id, owner_user_id, slug, title, summary, status, created_at)
select 1, 1, 'solar-for-neighbors', 'Solar For Neighbors', 'A cooperative toolkit for apartment blocks to coordinate balcony solar installs.', 'active', timestamp '2026-04-22 09:00:00'
where not exists (select 1 from projects where id = 1);

insert into projects (id, owner_user_id, slug, title, summary, status, created_at)
select 2, 2, 'neighborhood-tool-library', 'Neighborhood Tool Library', 'A simple way for neighbors to share tools, booking slots, and repair know-how.', 'active', timestamp '2026-04-20 14:30:00'
where not exists (select 1 from projects where id = 2);

insert into projects (id, owner_user_id, slug, title, summary, status, created_at)
select 3, 3, 'campus-climate-hub', 'Campus Climate Hub', 'A student-run workspace for climate action projects, onboarding, and volunteer shifts.', 'completed', timestamp '2026-04-12 11:15:00'
where not exists (select 1 from projects where id = 3);

insert into projects (id, owner_user_id, slug, title, summary, status, created_at)
select 4, 4, 'community-repair-bus', 'Community Repair Bus', 'A dormant concept for a mobile repair workshop that visits small towns on weekends.', 'dormant', timestamp '2026-04-01 08:45:00'
where not exists (select 1 from projects where id = 4);

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 1, 'Founder + Product', '10 hrs/week', true, false, 0
where not exists (select 1 from project_roles where project_id = 1 and title = 'Founder + Product');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 1, 'Android Engineer', '6 hrs/week', false, true, 1
where not exists (select 1 from project_roles where project_id = 1 and title = 'Android Engineer');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 1, 'Community Researcher', '4 hrs/week', false, true, 2
where not exists (select 1 from project_roles where project_id = 1 and title = 'Community Researcher');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 2, 'Founder + Ops', '8 hrs/week', true, false, 0
where not exists (select 1 from project_roles where project_id = 2 and title = 'Founder + Ops');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 2, 'Frontend Engineer', '5 hrs/week', false, true, 1
where not exists (select 1 from project_roles where project_id = 2 and title = 'Frontend Engineer');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 2, 'Partnerships Lead', '3 hrs/week', false, true, 2
where not exists (select 1 from project_roles where project_id = 2 and title = 'Partnerships Lead');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 3, 'Founder + Facilitator', '7 hrs/week', true, false, 0
where not exists (select 1 from project_roles where project_id = 3 and title = 'Founder + Facilitator');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 3, 'Event Designer', '2 hrs/week', false, true, 1
where not exists (select 1 from project_roles where project_id = 3 and title = 'Event Designer');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 4, 'Founder + Mechanical Design', '5 hrs/week', true, false, 0
where not exists (select 1 from project_roles where project_id = 4 and title = 'Founder + Mechanical Design');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
select 4, 'Electrical Engineer', '4 hrs/week', false, true, 1
where not exists (select 1 from project_roles where project_id = 4 and title = 'Electrical Engineer');
