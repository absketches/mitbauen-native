import type { Dictionary, Language } from '../i18n'
import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { ApiError } from '../api'
import { descriptionForLanguage } from '../projectDescriptions'
import type { ProjectComment, ProjectCommentPayload, ProjectDetails, ProjectImage } from '../types'
import { ProjectImageView } from './ProjectImageView'
import { SafeMarkdown } from './SafeMarkdown'

type DetailNotice = 'created' | 'updated' | 'createdWithMediaWarning' | 'updatedWithMediaWarning' | null

type ProjectDetailViewProps = {
  copy: Dictionary['projectDetail']
  language: Language
  slug: string
  notice: DetailNotice
  refreshKey: number
  canViewComments: boolean
  onLoadProject: (slug: string) => Promise<ProjectDetails>
  onLoadComments: (slug: string) => Promise<ProjectComment[]>
  onCreateComment: (slug: string, payload: ProjectCommentPayload) => Promise<ProjectComment>
  onMarkCommentsRead: (slug: string) => Promise<{ read: boolean }>
  onCommentsChanged: () => void
  onOpenFounderProfile: (publicId: string) => void
  onEdit: (slug: string) => void
  onDelete: (slug: string) => Promise<void>
  onBackToFeed: (slug: string, notice: DetailNotice) => void
}

export function ProjectDetailView({
  copy,
  language,
  slug,
  notice,
  refreshKey,
  canViewComments,
  onLoadProject,
  onLoadComments,
  onCreateComment,
  onMarkCommentsRead,
  onCommentsChanged,
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
  const [comments, setComments] = useState<ProjectComment[]>([])
  const [commentsLoading, setCommentsLoading] = useState(false)
  const [commentsError, setCommentsError] = useState<string | null>(null)
  const [commentBody, setCommentBody] = useState('')
  const [commentSubmitting, setCommentSubmitting] = useState(false)
  const [commentError, setCommentError] = useState<string | null>(null)
  const [expandedImage, setExpandedImage] = useState<ProjectImage | null>(null)

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
        setError(nextError instanceof ApiError ? copy.loadError : nextError instanceof Error ? nextError.message : copy.loadError)
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [copy.loadError, onLoadProject, refreshKey, slug])

  useEffect(() => {
    let cancelled = false

    if (!canViewComments) {
      setComments([])
      setCommentsLoading(false)
      setCommentsError(null)
      return
    }

    setCommentsLoading(true)
    setCommentsError(null)

    onLoadComments(slug)
      .then((nextComments) => {
        if (cancelled) {
          return
        }
        setComments(nextComments)
        setCommentsLoading(false)
        void onMarkCommentsRead(slug)
          .then(onCommentsChanged)
          .catch((nextError) => console.error(nextError))
      })
      .catch((nextError) => {
        if (cancelled) {
          return
        }
        setCommentsError(commentErrorMessage(nextError, copy, copy.commentsLoadError))
        setCommentsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [canViewComments, copy.commentsLoadError, onLoadComments, onMarkCommentsRead, refreshKey, slug])

  useEffect(() => {
    if (!expandedImage) {
      return
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setExpandedImage(null)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [expandedImage])

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
  const description = descriptionForLanguage(currentProject, language)

  async function handleDelete() {
    if (!window.confirm(copy.deleteConfirm)) {
      return
    }
    setDeleting(true)
    setDeleteError(null)
    try {
      await onDelete(currentProject.slug)
    } catch (nextError) {
      setDeleteError(nextError instanceof ApiError ? copy.deleteError : nextError instanceof Error ? nextError.message : copy.deleteError)
    } finally {
      setDeleting(false)
    }
  }

  async function handleCommentSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextBody = commentBody.trim()
    if (!nextBody) {
      setCommentError(copy.commentEmptyError)
      return
    }
    setCommentSubmitting(true)
    setCommentError(null)
    try {
      const nextComment = await onCreateComment(currentProject.slug, { body: nextBody })
      setComments((current) => [...current, nextComment])
      setCommentBody('')
      await onMarkCommentsRead(currentProject.slug)
      onCommentsChanged()
    } catch (nextError) {
      setCommentError(commentErrorMessage(nextError, copy, copy.commentError))
    } finally {
      setCommentSubmitting(false)
    }
  }

  return (
    <section className="project-detail">
      {notice ? (
        <p className="state-card project-detail__notice">
          {noticeCopy(copy, notice)}
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
          {description ? (
            <SafeMarkdown className="project-detail__body" text={description} />
          ) : (
            <p className="project-detail__empty">{copy.descriptionMissing}</p>
          )}
          {currentProject.images && currentProject.images.length > 0 ? (
            <ul className="project-detail__media-grid">
              {currentProject.images.map((image) => (
                <li key={image.id}>
                  <button className="project-detail__image-button" type="button" onClick={() => setExpandedImage(image)}>
                    <ProjectImageView src={image.url} alt={image.altText} fallback={copy.imageUnavailable} />
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </article>

        <aside className="project-detail__stack">
          <article className="project-detail__panel project-detail__panel--dark">
            <p className="hero__eyebrow">{copy.commitmentEyebrow}</p>
            <h2>{currentProject.founder.role}</h2>
            <SafeMarkdown text={currentProject.founder.commitment} />
          </article>

          <article className="project-detail__panel">
            <p className="hero__eyebrow">{copy.openRolesEyebrow}</p>
            <h2>{copy.openRolesTitle(currentProject.openRoles.length)}</h2>
            <ul className="project-detail__roles">
              {currentProject.openRoles.map((role) => (
                <li key={`${currentProject.slug}-${role.title}`}>
                  <strong>{role.title}</strong>
                  <SafeMarkdown text={role.commitment} />
                </li>
              ))}
            </ul>
          </article>

          {currentProject.links && currentProject.links.length > 0 ? (
            <article className="project-detail__panel">
              <p className="hero__eyebrow">{copy.linksEyebrow}</p>
              <h2>{copy.linksTitle}</h2>
              <ul className="project-detail__links">
                {currentProject.links.map((link) => (
                  <li key={`${link.label}-${link.url}`}>
                    <a href={link.url} target="_blank" rel="noopener noreferrer">
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </article>
          ) : null}
        </aside>
      </div>

      <section className="project-detail__comments">
        <div className="project-detail__comments-header">
          <div>
            <p className="hero__eyebrow">{copy.commentsEyebrow}</p>
            <h2>
              {copy.commentsTitle}
              {canViewComments && comments.length > 0 ? <span> ({comments.length})</span> : null}
            </h2>
          </div>
        </div>

        {!canViewComments ? (
          <p className="state-card">{copy.commentsMembersOnly}</p>
        ) : commentsLoading ? (
          <p className="state-card">{copy.commentsLoading}</p>
        ) : commentsError ? (
          <p className="state-card state-card--error">{commentsError}</p>
        ) : (
          <>
            {comments.length === 0 ? (
              <p className="state-card">{copy.commentsEmpty}</p>
            ) : (
              <div className="project-detail__comment-list">
                {comments.map((comment) => (
                  <article className="project-detail__comment" key={comment.id}>
                    <div className="project-detail__comment-avatar" aria-hidden="true">
                      {comment.authorDisplayName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <div className="project-detail__comment-meta">
                        <button className="founder-link" type="button" onClick={() => onOpenFounderProfile(comment.authorPublicId)}>
                          {comment.authorDisplayName}
                        </button>
                        <span>{new Date(comment.createdAt).toLocaleDateString()}</span>
                      </div>
                      <SafeMarkdown className="project-detail__comment-body" text={comment.body} />
                    </div>
                  </article>
                ))}
              </div>
            )}

            <form className="project-detail__comment-form" onSubmit={handleCommentSubmit}>
              <textarea
                aria-label={copy.commentPlaceholder}
                value={commentBody}
                onChange={(event) => setCommentBody(event.target.value)}
                placeholder={copy.commentPlaceholder}
                maxLength={1000}
                rows={4}
                required
              />
              <p className="auth-note">{copy.markdownHint}</p>
              {commentError ? <p className="auth-error">{commentError}</p> : null}
              <div className="project-detail__comment-actions">
                <button className="primary-button" type="submit" disabled={commentSubmitting}>
                  {commentSubmitting ? copy.postingComment : copy.postComment}
                </button>
              </div>
            </form>
          </>
        )}
      </section>

      {expandedImage ? (
        <div className="image-viewer" role="presentation" onClick={() => setExpandedImage(null)}>
          <div
            className="image-viewer__dialog"
            role="dialog"
            aria-modal="true"
            aria-label={expandedImage.altText || currentProject.title}
            onClick={(event) => event.stopPropagation()}
          >
            <button className="image-viewer__close" type="button" onClick={() => setExpandedImage(null)} aria-label="Close image">
              x
            </button>
            <img src={expandedImage.url} alt={expandedImage.altText} />
          </div>
        </div>
      ) : null}
    </section>
  )
}

function commentErrorMessage(error: unknown, copy: Dictionary['projectDetail'], fallback: string) {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'PROJECT_COMMENTS_AUTH_REQUIRED':
      case 'PROJECT_COMMENTS_EMAIL_UNVERIFIED':
        return copy.commentsMembersOnly
      case 'PROJECT_COMMENT_EMPTY':
        return copy.commentEmptyError
      case 'PROJECT_COMMENT_TOO_LONG':
        return copy.commentTooLongError
      default:
        return fallback
    }
  }
  return error instanceof Error ? error.message : fallback
}

function noticeCopy(copy: Dictionary['projectDetail'], notice: NonNullable<DetailNotice>) {
  switch (notice) {
    case 'created':
      return copy.noticeCreated
    case 'updated':
      return copy.noticeUpdated
    case 'createdWithMediaWarning':
      return copy.noticeCreatedWithMediaWarning
    case 'updatedWithMediaWarning':
      return copy.noticeUpdatedWithMediaWarning
  }
}
