import type { Dictionary } from '../i18n'
import type { Project } from '../types'

type ProjectCardProps = {
  copy: Dictionary['projectCard']
  project: Project
  highlighted?: boolean
  onOpen?: (slug: string) => void
  onOpenFounderProfile?: (publicId: string) => void
}

export function ProjectCard({ copy, project, highlighted = false, onOpen, onOpenFounderProfile }: ProjectCardProps) {
  const descriptionPreview =
    project.description.length > 190 ? `${project.description.slice(0, 187).trimEnd()}...` : project.description

  return (
    <article
      className={`project-card${highlighted ? ' project-card--highlighted' : ''}`}
      data-project-slug={project.slug}
      data-testid={`project-${project.slug}`}
    >
      <div className="project-card__header">
        <span className={`status-pill status-pill--${project.status}`}>{copy.status[project.status]}</span>
        <h2>{project.title}</h2>
      </div>

      <p className="project-card__summary">{descriptionPreview}</p>

      <dl className="project-card__meta">
        <div>
          <dt>{copy.founder}</dt>
          <dd>
            {onOpenFounderProfile ? (
              <button className="founder-link" type="button" onClick={() => onOpenFounderProfile(project.founder.publicId)}>
                {project.founder.name}
              </button>
            ) : (
              project.founder.name
            )}
          </dd>
        </div>
        <div>
          <dt>{copy.ownerRole}</dt>
          <dd>{project.founder.role}</dd>
        </div>
        <div>
          <dt>{copy.commitment}</dt>
          <dd>{project.founder.commitment}</dd>
        </div>
      </dl>

      <section className="project-card__roles">
        <h3>{copy.openRoles}</h3>
        <ul>
          {project.openRoles.map((role) => (
            <li key={`${project.id}-${role.title}`}>
              <strong>{role.title}</strong>
              <span>{role.commitment}</span>
            </li>
          ))}
        </ul>
      </section>

      {onOpen ? (
        <button className="ghost-button project-card__cta" type="button" onClick={() => onOpen(project.slug)}>
          {copy.viewProject}
        </button>
      ) : null}
    </article>
  )
}
