import type { Dictionary } from '../i18n'
import { useEffect, useState } from 'react'
import type { ProjectDetails } from '../types'

type DetailNotice = 'created' | 'updated' | null

type ProjectDetailViewProps = {
  copy: Dictionary['projectDetail']
  slug: string
  notice: DetailNotice
  onLoadProject: (slug: string) => Promise<ProjectDetails>
  onOpenFounderProfile: (publicId: string) => void
  onEdit: (slug: string) => void
  onDelete: (slug: string) => Promise<void>
  onBackToFeed: (slug: string, notice: DetailNotice) => void
}

export function ProjectDetailView({
  copy,
  slug,
  notice,
  onLoadProject,
  onOpenFounderProfile,
  onEdit,
  onDelete,
  onBackToFeed,
}: ProjectDetailViewProps) {
  const [project, setProject] = useState<ProjectDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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
        setError(nextError instanceof Error ? nextError.message : copy.loadError)
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [copy.loadError, onLoadProject, slug])

  if (loading) {
    return <p className="state-card">{copy.loading}</p>
  }

  if (error) {
    return <p className="state-card state-card--error">{error}</p>
  }

  if (!project) {
    return <p className="state-card state-card--error">{copy.notFound}</p>
  }

  const currentProject = project

  async function handleDelete() {
    if (!window.confirm(copy.deleteConfirm)) {
      return
    }
    setDeleting(true)
    setDeleteError(null)
    try {
      await onDelete(currentProject.slug)
    } catch (nextError) {
      setDeleteError(nextError instanceof Error ? nextError.message : copy.deleteError)
      setDeleting(false)
    }
  }

  return (
    <section className="project-detail">
      {notice ? (
        <p className="state-card project-detail__notice">
          {notice === 'created' ? copy.noticeCreated : copy.noticeUpdated}
        </p>
      ) : null}
      {deleteError ? <p className="state-card state-card--error">{deleteError}</p> : null}

      <div className="project-detail__header">
        <div className="project-detail__title-block">
          <span className={`status-pill status-pill--${currentProject.status}`}>{copy.status[currentProject.status]}</span>
          <p className="hero__eyebrow">{copy.eyebrow}</p>
          <h1>{currentProject.title}</h1>
          <p className="project-detail__meta-copy">
            {copy.ledByPrefix}
            <button className="founder-link" type="button" onClick={() => onOpenFounderProfile(currentProject.founder.publicId)}>
              {currentProject.founder.name}
            </button>
            {copy.ledBySuffix(currentProject.founder.role)}
          </p>
        </div>

        <div className="project-detail__actions">
          <button className="ghost-button" type="button" onClick={() => onBackToFeed(currentProject.slug, notice)}>
            {copy.back}
          </button>
          {currentProject.canManage ? (
            <>
              <button className="primary-button" type="button" onClick={() => onEdit(currentProject.slug)}>
                {copy.edit}
              </button>
              <button className="ghost-button ghost-button--danger" type="button" onClick={() => void handleDelete()} disabled={deleting}>
                {deleting ? copy.deleting : copy.delete}
              </button>
            </>
          ) : null}
        </div>
      </div>

      <div className="project-detail__grid">
        <article className="project-detail__panel project-detail__panel--primary">
          <p className="hero__eyebrow">{copy.whatEyebrow}</p>
          <h2>{copy.whatTitle}</h2>
          <p className="project-detail__body">{currentProject.description}</p>
        </article>

        <aside className="project-detail__stack">
          <article className="project-detail__panel project-detail__panel--dark">
            <p className="hero__eyebrow">{copy.commitmentEyebrow}</p>
            <h2>{currentProject.founder.role}</h2>
            <p>{currentProject.founder.commitment}</p>
          </article>

          <article className="project-detail__panel">
            <p className="hero__eyebrow">{copy.openRolesEyebrow}</p>
            <h2>{copy.openRolesTitle(currentProject.openRoles.length)}</h2>
            <ul className="project-detail__roles">
              {currentProject.openRoles.map((role) => (
                <li key={`${currentProject.slug}-${role.title}`}>
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
