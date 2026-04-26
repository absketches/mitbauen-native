import type { Project } from '../types'

type ProjectCardProps = {
  project: Project
}

const statusLabels: Record<Project['status'], string> = {
  active: 'Active',
  completed: 'Completed',
  dormant: 'Dormant',
}

export function ProjectCard({ project }: ProjectCardProps) {
  return (
    <article className="project-card" data-testid={`project-${project.slug}`}>
      <div className="project-card__header">
        <span className={`status-pill status-pill--${project.status}`}>{statusLabels[project.status]}</span>
        <h2>{project.title}</h2>
      </div>

      <p className="project-card__summary">{project.summary}</p>

      <dl className="project-card__meta">
        <div>
          <dt>Founder</dt>
          <dd>{project.founder.name}</dd>
        </div>
        <div>
          <dt>Owner Role</dt>
          <dd>{project.founder.role}</dd>
        </div>
        <div>
          <dt>Commitment</dt>
          <dd>{project.founder.commitment}</dd>
        </div>
      </dl>

      <section className="project-card__roles">
        <h3>Open Roles</h3>
        <ul>
          {project.openRoles.map((role) => (
            <li key={`${project.id}-${role.title}`}>
              <strong>{role.title}</strong>
              <span>{role.commitment}</span>
            </li>
          ))}
        </ul>
      </section>
    </article>
  )
}
