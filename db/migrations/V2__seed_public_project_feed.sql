insert into users (id, handle, display_name) values
    (1, 'avery', 'Avery Bloom'),
    (2, 'nora', 'Nora Patel'),
    (3, 'elliot', 'Elliot Kim'),
    (4, 'mika', 'Mika Rossi');

insert into projects (id, owner_user_id, slug, title, summary, status, created_at) values
    (1, 1, 'solar-for-neighbors', 'Solar For Neighbors', 'A cooperative toolkit for apartment blocks to coordinate balcony solar installs.', 'active', timestamp '2026-04-22 09:00:00'),
    (2, 2, 'neighborhood-tool-library', 'Neighborhood Tool Library', 'A simple way for neighbors to share tools, booking slots, and repair know-how.', 'active', timestamp '2026-04-20 14:30:00'),
    (3, 3, 'campus-climate-hub', 'Campus Climate Hub', 'A student-run workspace for climate action projects, onboarding, and volunteer shifts.', 'completed', timestamp '2026-04-12 11:15:00'),
    (4, 4, 'community-repair-bus', 'Community Repair Bus', 'A dormant concept for a mobile repair workshop that visits small towns on weekends.', 'dormant', timestamp '2026-04-01 08:45:00');

insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order) values
    (1, 'Founder + Product', '10 hrs/week', true, false, 0),
    (1, 'Android Engineer', '6 hrs/week', false, true, 1),
    (1, 'Community Researcher', '4 hrs/week', false, true, 2),
    (2, 'Founder + Ops', '8 hrs/week', true, false, 0),
    (2, 'Frontend Engineer', '5 hrs/week', false, true, 1),
    (2, 'Partnerships Lead', '3 hrs/week', false, true, 2),
    (3, 'Founder + Facilitator', '7 hrs/week', true, false, 0),
    (3, 'Event Designer', '2 hrs/week', false, true, 1),
    (4, 'Founder + Mechanical Design', '5 hrs/week', true, false, 0),
    (4, 'Electrical Engineer', '4 hrs/week', false, true, 1);
