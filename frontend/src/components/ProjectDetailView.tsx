import { useEffect, useState } from 'react'
import type { ProjectDetails } from '../types'

type DetailNotice = 'created' | 'updated' | null

type ProjectDetailViewProps = {
  slug: string
  notice: DetailNotice
  currentUserId?: number
  onLoadProject: (slug: string) => Promise<ProjectDetails>
  onEdit: (slug: string) => void
  onBackToFeed: (slug: string, notice: DetailNotice) => void
}

const noticeCopy: Record<Exclude<DetailNotice, null>, string> = {
  created: 'Project created. It is ready for people to discover, and the feed can highlight it for you.',
  updated: 'Project updated. The detail page and feed will now reflect the latest version.',
}

const statusLabels: Record<ProjectDetails['status'], string> = {
  active: 'Active',
  completed: 'Completed',
  dormant: 'Dormant',
}

export function ProjectDetailView({
  slug,
  notice,
  currentUserId,
  onLoadProject,
  onEdit,
  onBackToFeed,
}: ProjectDetailViewProps) {
  const [project, setProject] = useState<ProjectDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    setLoading(true)
    setError(null)

    onLoadProject(slug)
      .then((nextProject) => {
        if (cancelled) {
          return
        }
        setProject(nextProject)
        setLoading(false)
      })
      .catch((nextError) => {
        if (cancelled) {
          return
        }
        setError(nextError instanceof Error ? nextError.message : 'We could not load this project right now.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [onLoadProject, slug])

  if (loading) {
    return <p className="state-card">Loading project...</p>
  }

  if (error) {
    return <p className="state-card state-card--error">{error}</p>
  }

  if (!project) {
    return <p className="state-card state-card--error">Project not found.</p>
  }

  const canEdit = currentUserId === project.ownerUserId

  return (
    <section className="project-detail">
      {notice ? <p className="state-card project-detail__notice">{noticeCopy[notice]}</p> : null}

      <div className="project-detail__header">
        <div className="project-detail__title-block">
          <span className={`status-pill status-pill--${project.status}`}>{statusLabels[project.status]}</span>
          <p className="hero__eyebrow">Founder-led project</p>
          <h1>{project.title}</h1>
          <p className="project-detail__meta-copy">
            Led by {project.founder.name} as {project.founder.role}.
          </p>
        </div>

        <div className="project-detail__actions">
          <button className="ghost-button" type="button" onClick={() => onBackToFeed(project.slug, notice)}>
            Back to feed
          </button>
          {canEdit ? (
            <button className="primary-button" type="button" onClick={() => onEdit(project.slug)}>
              Edit project
            </button>
          ) : null}
        </div>
      </div>

      <div className="project-detail__grid">
        <article className="project-detail__panel project-detail__panel--primary">
          <p className="hero__eyebrow">What is being built</p>
          <h2>Project description</h2>
          <p className="project-detail__body">{project.description}</p>
        </article>

        <aside className="project-detail__stack">
          <article className="project-detail__panel project-detail__panel--dark">
            <p className="hero__eyebrow">Founder commitment</p>
            <h2>{project.founder.role}</h2>
            <p>{project.founder.commitment}</p>
          </article>

          <article className="project-detail__panel">
            <p className="hero__eyebrow">Open roles</p>
            <h2>{project.openRoles.length} ways to contribute</h2>
            <ul className="project-detail__roles">
              {project.openRoles.map((role) => (
                <li key={`${project.slug}-${role.title}`}>
                  <strong>{role.title}</strong>
                  <span>{role.commitment}</span>
                </li>
              ))}
            </ul>
          </article>
        </aside>
      </div>
    </section>
  )
}
