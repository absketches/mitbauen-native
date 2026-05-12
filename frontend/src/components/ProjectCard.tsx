import type { Dictionary } from '../i18n'
import type { Project } from '../types'
import { ProjectImageView } from './ProjectImageView'
import { markdownPreview } from './SafeMarkdown'

type ProjectCardProps = {
  copy: Dictionary['projectCard']
  project: Project
  highlighted?: boolean
  onOpen?: (slug: string) => void
  onOpenFounderProfile?: (publicId: string) => void
}

export function ProjectCard({ copy, project, highlighted = false, onOpen, onOpenFounderProfile }: ProjectCardProps) {
  const descriptionPreview = markdownPreview(project.description)
  const visibleRoles = project.openRoles.slice(0, 3)
  const extraRoleCount = project.openRoles.length - visibleRoles.length
  const thumbnail = project.images?.[0]

  const cardClassName = [
    'project-card',
    thumbnail ? 'project-card--with-image' : 'project-card--without-image',
    highlighted ? 'project-card--highlighted' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <article
      className={cardClassName}
      data-project-slug={project.slug}
      data-testid={`project-${project.slug}`}
    >
      <div className="project-card__header">
        <span className={`status-pill status-pill--${project.status}`}>{copy.status[project.status]}</span>
        <h2>{project.title}</h2>
      </div>

      {thumbnail ? (
        <div className="project-card__media">
          <ProjectImageView className="project-card__thumbnail" src={thumbnail.url} alt={thumbnail.altText} fallback="" />
        </div>
      ) : null}

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
      </dl>

      <section className="project-card__roles">
        <h3>{copy.openRoles}</h3>
        <ul className="project-card__role-chips">
          {visibleRoles.map((role) => (
            <li key={`${project.id}-${role.title}`}>
              {role.title}
            </li>
          ))}
          {extraRoleCount > 0 ? <li>{copy.moreRoles(extraRoleCount)}</li> : null}
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
