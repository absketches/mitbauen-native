import type { FormEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api'
import type { Dictionary } from '../i18n'
import type { OpenRole, ProjectDetails, ProjectImage, ProjectImageChanges, ProjectLink, ProjectPayload } from '../types'
import { ProjectImageView } from './ProjectImageView'

type ProjectFormViewProps = {
  copy: Dictionary['projectForm']
  mode: 'create' | 'edit'
  slug?: string
  loadProject?: (slug: string) => Promise<ProjectDetails>
  onSubmit: (payload: ProjectPayload, imageChanges: ProjectImageChanges) => Promise<void>
  onCancel: () => void
}

type NewProjectImage = {
  file: File
  previewUrl: string
}

const BLANK_ROLE: OpenRole = { title: '', commitment: '' }
const BLANK_LINK: ProjectLink = { label: '', url: '' }
const PROJECT_TITLE_MAX_LENGTH = 120
const PROJECT_DESCRIPTION_MAX_LENGTH = 3000
const FOUNDER_ROLE_MAX_LENGTH = 120
const FOUNDER_COMMITMENT_MAX_LENGTH = 500
const OPEN_ROLE_TITLE_MAX_LENGTH = 120
const OPEN_ROLE_COMMITMENT_MAX_LENGTH = 500
const PROJECT_LINKS_MAX_COUNT = 8
const PROJECT_LINK_LABEL_MAX_LENGTH = 40
const PROJECT_IMAGES_MAX_COUNT = 5
const PROJECT_IMAGE_MAX_BYTES = 2 * 1024 * 1024
const PROJECT_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export function ProjectFormView({
  copy,
  mode,
  slug,
  loadProject,
  onSubmit,
  onCancel,
}: ProjectFormViewProps) {
  const [loadingProject, setLoadingProject] = useState(mode === 'edit')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [canManage, setCanManage] = useState(mode !== 'edit')
  const [form, setForm] = useState<ProjectPayload>({
    title: '',
    description: '',
    founderRole: '',
    founderCommitment: '',
    openRoles: [{ ...BLANK_ROLE }],
    links: [],
  })
  const [existingImages, setExistingImages] = useState<ProjectImage[]>([])
  const [removedImageIds, setRemovedImageIds] = useState<number[]>([])
  const [newImages, setNewImages] = useState<NewProjectImage[]>([])
  const newImagesRef = useRef<NewProjectImage[]>([])
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    newImagesRef.current = newImages
  }, [newImages])

  useEffect(() => () => {
    newImagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
  }, [])

  useEffect(() => {
    let cancelled = false

    if (mode !== 'edit' || !slug || !loadProject) {
      setCanManage(true)
      setLoadingProject(false)
      return
    }

    setLoadingProject(true)
    setLoadError(null)
    setCanManage(false)

    loadProject(slug)
      .then((project) => {
        if (cancelled) {
          return
        }
        setCanManage(project.canManage)
        setForm({
          title: project.title,
          description: project.description,
          founderRole: project.founder.role,
          founderCommitment: project.founder.commitment,
          openRoles: project.openRoles.length > 0 ? project.openRoles : [{ ...BLANK_ROLE }],
          links: project.links ?? [],
        })
        setExistingImages(project.images ?? [])
        setRemovedImageIds([])
        clearNewImages()
        setLoadingProject(false)
      })
      .catch(() => {
        if (cancelled) {
          return
        }
        setLoadError(copy.loadError)
        setLoadingProject(false)
      })

    return () => {
      cancelled = true
    }
  }, [copy.loadError, loadProject, mode, slug])

  function updateField(field: 'title' | 'description' | 'founderRole' | 'founderCommitment', value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function updateRole(index: number, field: keyof OpenRole, value: string) {
    setForm((current) => ({
      ...current,
      openRoles: current.openRoles.map((role, roleIndex) =>
        roleIndex === index ? { ...role, [field]: value } : role,
      ),
    }))
  }

  function addRole() {
    setForm((current) => ({
      ...current,
      openRoles: [...current.openRoles, { ...BLANK_ROLE }],
    }))
  }

  function updateLink(index: number, field: keyof ProjectLink, value: string) {
    setForm((current) => ({
      ...current,
      links: current.links.map((link, linkIndex) =>
        linkIndex === index ? { ...link, [field]: value } : link,
      ),
    }))
  }

  function addLink() {
    setForm((current) => ({
      ...current,
      links: [...current.links, { ...BLANK_LINK }],
    }))
  }

  function removeLink(index: number) {
    setForm((current) => ({
      ...current,
      links: current.links.filter((_, linkIndex) => linkIndex !== index),
    }))
  }

  function addImages(files: FileList | null) {
    if (!files) {
      return
    }
    setNewImages((current) => {
      const remainingSlots = PROJECT_IMAGES_MAX_COUNT - existingImages.length - current.length
      if (remainingSlots <= 0) {
        return current
      }
      const acceptedFiles = Array.from(files).slice(0, remainingSlots)
      return [
        ...current,
        ...acceptedFiles.map((file) => ({
          file,
          previewUrl: URL.createObjectURL(file),
        })),
      ]
    })
  }

  function removeExistingImage(imageId: number) {
    setExistingImages((current) => current.filter((image) => image.id !== imageId))
    setRemovedImageIds((current) => [...current, imageId])
  }

  function removeNewImage(index: number) {
    setNewImages((current) => {
      const removed = current[index]
      if (removed) {
        URL.revokeObjectURL(removed.previewUrl)
      }
      return current.filter((_, imageIndex) => imageIndex !== index)
    })
  }

  function clearNewImages() {
    setNewImages((current) => {
      current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
      return []
    })
  }

  function removeRole(index: number) {
    setForm((current) => ({
      ...current,
      openRoles: current.openRoles.filter((_, roleIndex) => roleIndex !== index),
    }))
  }

  function validate(nextForm: ProjectPayload) {
    const errors: Record<string, string> = {}

    if (nextForm.title.trim().length < 5 || nextForm.title.trim().length > PROJECT_TITLE_MAX_LENGTH) {
      errors.title = copy.validationTitle
    }
    if (nextForm.description.trim().length < 40 || nextForm.description.trim().length > PROJECT_DESCRIPTION_MAX_LENGTH) {
      errors.description = copy.validationDescription
    }
    if (nextForm.founderRole.trim().length < 3 || nextForm.founderRole.trim().length > FOUNDER_ROLE_MAX_LENGTH) {
      errors.founderRole = copy.validationFounderRole
    }
    if (nextForm.founderCommitment.trim().length < 5 || nextForm.founderCommitment.trim().length > FOUNDER_COMMITMENT_MAX_LENGTH) {
      errors.founderCommitment = copy.validationFounderCommitment
    }
    if (nextForm.openRoles.length < 1) {
      errors.openRoles = copy.validationOpenRolesMin
    }
    if (nextForm.openRoles.length > 6) {
      errors.openRoles = copy.validationOpenRolesMax
    }
    if (nextForm.links.length > PROJECT_LINKS_MAX_COUNT) {
      errors.links = copy.validationLinksMax
    }
    if (existingImages.length + newImages.length > PROJECT_IMAGES_MAX_COUNT) {
      errors.images = copy.validationImagesMax
    }
    if (newImages.some((image) => !PROJECT_IMAGE_TYPES.includes(image.file.type))) {
      errors.images = copy.validationImageType
    }
    if (newImages.some((image) => image.file.size <= 0 || image.file.size > PROJECT_IMAGE_MAX_BYTES)) {
      errors.images = copy.validationImageSize
    }

    nextForm.openRoles.forEach((role, index) => {
      if (role.title.trim().length < 3 || role.title.trim().length > OPEN_ROLE_TITLE_MAX_LENGTH) {
        errors[`openRoleTitle_${index}`] = copy.validationRoleTitle
      }
      if (role.commitment.trim().length < 3 || role.commitment.trim().length > OPEN_ROLE_COMMITMENT_MAX_LENGTH) {
        errors[`openRoleCommitment_${index}`] = copy.validationRoleCommitment
      }
    })
    nextForm.links.forEach((link, index) => {
      if (link.label.trim().length < 2 || link.label.trim().length > PROJECT_LINK_LABEL_MAX_LENGTH) {
        errors[`linkLabel_${index}`] = copy.validationLinkLabel
      }
      if (!isValidHttpUrl(link.url.trim())) {
        errors[`linkUrl_${index}`] = copy.validationLinkUrl
      }
    })

    return errors
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validate(form)
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      setFormError(null)
      return
    }

    setFieldErrors({})
    setFormError(null)
    setSubmitting(true)

    try {
      await onSubmit({
        title: form.title.trim(),
        description: form.description.trim(),
        founderRole: form.founderRole.trim(),
        founderCommitment: form.founderCommitment.trim(),
        openRoles: form.openRoles.map((role) => ({
          title: role.title.trim(),
          commitment: role.commitment.trim(),
        })),
        links: form.links.map((link) => ({
          label: link.label.trim(),
          url: link.url.trim(),
        })),
      }, {
        newImages: newImages.map((image) => image.file),
        removedImageIds,
      })
    } catch (error) {
      setFormError(error instanceof ApiError ? copy.saveError : error instanceof Error ? error.message : copy.saveError)
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingProject) {
    return <p className="state-card">{copy.loading}</p>
  }

  if (loadError) {
    return <p className="state-card state-card--error">{loadError}</p>
  }

  if (mode === 'edit' && !canManage) {
    return (
      <section className="state-shell">
        <article className="state-card state-card--error">
          {copy.ownerOnly}
        </article>
        <button className="ghost-button" type="button" onClick={onCancel}>
          {copy.backToProject}
        </button>
      </section>
    )
  }

  return (
    <section className="project-editor">
      <div className="project-editor__intro">
        <p className="hero__eyebrow">{mode === 'create' ? copy.createEyebrow : copy.editEyebrow}</p>
        <h1>{mode === 'create' ? copy.createTitle : copy.editTitle}</h1>
        <p className="project-editor__copy">
          {copy.introCopy}
        </p>
      </div>

      <form className="project-form" onSubmit={handleSubmit}>
        {formError ? <p className="auth-error">{formError}</p> : null}

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.ideaEyebrow}</p>
            <h2>{copy.ideaTitle}</h2>
          </div>

          <label>
            {copy.titleLabel}
            <input
              aria-label={copy.titleLabel}
              value={form.title}
              onChange={(event) => updateField('title', event.target.value)}
              type="text"
              maxLength={PROJECT_TITLE_MAX_LENGTH}
              required
            />
            {fieldErrors.title ? <span className="project-form__error">{fieldErrors.title}</span> : null}
          </label>

          <label>
            {copy.descriptionLabel}
            <textarea
              aria-label={copy.descriptionLabel}
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
              rows={9}
              maxLength={PROJECT_DESCRIPTION_MAX_LENGTH}
              required
            />
            {form.description.length >= PROJECT_DESCRIPTION_MAX_LENGTH ? (
              <span className="project-form__limit" aria-live="polite">
                {copy.limitReached(PROJECT_DESCRIPTION_MAX_LENGTH)}
              </span>
            ) : null}
            <span className="auth-note">{copy.markdownHint}</span>
            {fieldErrors.description ? <span className="project-form__error">{fieldErrors.description}</span> : null}
          </label>

          <div className="project-form__image-field">
            <label>
              {copy.imagesLabel}
              <input
                aria-label={copy.imagesLabel}
                type="file"
                accept={PROJECT_IMAGE_TYPES.join(',')}
                multiple
                onChange={(event) => {
                  addImages(event.target.files)
                  event.target.value = ''
                }}
                disabled={existingImages.length + newImages.length >= PROJECT_IMAGES_MAX_COUNT}
              />
            </label>
            <span className="auth-note">{copy.imagesHint(PROJECT_IMAGES_MAX_COUNT)}</span>
            {fieldErrors.images ? <span className="project-form__error">{fieldErrors.images}</span> : null}

            {existingImages.length > 0 || newImages.length > 0 ? (
              <ul className="project-form__image-list">
                {existingImages.map((image) => (
                  <li key={`existing-${image.id}`}>
                    <ProjectImageView src={image.url} alt="" fallback={copy.savedImageLabel} />
                    <span>{copy.savedImageLabel}</span>
                    <button className="ghost-button ghost-button--small" type="button" onClick={() => removeExistingImage(image.id)}>
                      {copy.removeImage}
                    </button>
                  </li>
                ))}
                {newImages.map((image, index) => (
                  <li key={image.previewUrl}>
                    <img src={image.previewUrl} alt="" />
                    <span>{image.file.name}</span>
                    <button className="ghost-button ghost-button--small" type="button" onClick={() => removeNewImage(index)}>
                      {copy.removeImage}
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.founderEyebrow}</p>
            <h2>{copy.founderTitle}</h2>
          </div>

          <label>
            {copy.founderRoleLabel}
            <input
              aria-label={copy.founderRoleLabel}
              value={form.founderRole}
              onChange={(event) => updateField('founderRole', event.target.value)}
              type="text"
              maxLength={FOUNDER_ROLE_MAX_LENGTH}
              required
            />
            {form.founderRole.length >= FOUNDER_ROLE_MAX_LENGTH ? (
              <span className="project-form__limit" aria-live="polite">
                {copy.limitReached(FOUNDER_ROLE_MAX_LENGTH)}
              </span>
            ) : null}
            {fieldErrors.founderRole ? <span className="project-form__error">{fieldErrors.founderRole}</span> : null}
          </label>

          <label>
            {copy.founderCommitmentLabel}
            <textarea
              aria-label={copy.founderCommitmentLabel}
              value={form.founderCommitment}
              onChange={(event) => updateField('founderCommitment', event.target.value)}
              rows={6}
              maxLength={FOUNDER_COMMITMENT_MAX_LENGTH}
              required
            />
            {form.founderCommitment.length >= FOUNDER_COMMITMENT_MAX_LENGTH ? (
              <span className="project-form__limit" aria-live="polite">
                {copy.limitReached(FOUNDER_COMMITMENT_MAX_LENGTH)}
              </span>
            ) : null}
            <span className="auth-note">{copy.markdownHint}</span>
            {fieldErrors.founderCommitment ? (
              <span className="project-form__error">{fieldErrors.founderCommitment}</span>
            ) : null}
          </label>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.openRolesEyebrow}</p>
            <h2>{copy.openRolesTitle}</h2>
          </div>

          {fieldErrors.openRoles ? <p className="project-form__error">{fieldErrors.openRoles}</p> : null}

          <div className="project-form__roles">
            {form.openRoles.map((role, index) => (
              <article className="project-form__role-card" key={`${index}-${mode}`}>
                <div className="project-form__role-header">
                  <strong>{copy.roleCardLabel(index + 1)}</strong>
                  {form.openRoles.length > 1 ? (
                    <button className="ghost-button ghost-button--small" type="button" onClick={() => removeRole(index)}>
                      {copy.removeRole}
                    </button>
                  ) : null}
                </div>

                <label>
                  {copy.roleTitleFieldLabel}
                  <input
                    aria-label={copy.roleTitleAriaLabel(index + 1)}
                    value={role.title}
                    onChange={(event) => updateRole(index, 'title', event.target.value)}
                    type="text"
                    maxLength={OPEN_ROLE_TITLE_MAX_LENGTH}
                    required
                  />
                  {role.title.length >= OPEN_ROLE_TITLE_MAX_LENGTH ? (
                    <span className="project-form__limit" aria-live="polite">
                      {copy.limitReached(OPEN_ROLE_TITLE_MAX_LENGTH)}
                    </span>
                  ) : null}
                  {fieldErrors[`openRoleTitle_${index}`] ? (
                    <span className="project-form__error">{fieldErrors[`openRoleTitle_${index}`]}</span>
                  ) : null}
                </label>

                <label>
                  {copy.roleCommitmentFieldLabel}
                  <textarea
                    aria-label={copy.roleCommitmentAriaLabel(index + 1)}
                    value={role.commitment}
                    onChange={(event) => updateRole(index, 'commitment', event.target.value)}
                    rows={5}
                    maxLength={OPEN_ROLE_COMMITMENT_MAX_LENGTH}
                    required
                  />
                  {role.commitment.length >= OPEN_ROLE_COMMITMENT_MAX_LENGTH ? (
                    <span className="project-form__limit" aria-live="polite">
                      {copy.limitReached(OPEN_ROLE_COMMITMENT_MAX_LENGTH)}
                    </span>
                  ) : null}
                  <span className="auth-note">{copy.markdownHint}</span>
                  {fieldErrors[`openRoleCommitment_${index}`] ? (
                    <span className="project-form__error">{fieldErrors[`openRoleCommitment_${index}`]}</span>
                  ) : null}
                </label>
              </article>
            ))}
          </div>

          <button
            className="ghost-button"
            type="button"
            onClick={addRole}
            disabled={form.openRoles.length >= 6}
          >
            {copy.addRole}
          </button>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.linksEyebrow}</p>
            <h2>{copy.linksTitle}</h2>
          </div>

          {fieldErrors.links ? <p className="project-form__error">{fieldErrors.links}</p> : null}

          <div className="project-form__roles">
            {form.links.map((link, index) => (
              <article className="project-form__role-card" key={`link-${index}`}>
                <div className="project-form__role-header">
                  <strong>{copy.linkCardLabel(index + 1)}</strong>
                  <button className="ghost-button ghost-button--small" type="button" onClick={() => removeLink(index)}>
                    {copy.removeLink}
                  </button>
                </div>
                <label>
                  {copy.linkLabelFieldLabel}
                  <input
                    aria-label={copy.linkLabelAriaLabel(index + 1)}
                    value={link.label}
                    onChange={(event) => updateLink(index, 'label', event.target.value)}
                    type="text"
                    maxLength={PROJECT_LINK_LABEL_MAX_LENGTH}
                    required
                  />
                  {fieldErrors[`linkLabel_${index}`] ? (
                    <span className="project-form__error">{fieldErrors[`linkLabel_${index}`]}</span>
                  ) : null}
                </label>
                <label>
                  {copy.linkUrlFieldLabel}
                  <input
                    aria-label={copy.linkUrlAriaLabel(index + 1)}
                    value={link.url}
                    onChange={(event) => updateLink(index, 'url', event.target.value)}
                    type="url"
                    required
                  />
                  {fieldErrors[`linkUrl_${index}`] ? (
                    <span className="project-form__error">{fieldErrors[`linkUrl_${index}`]}</span>
                  ) : null}
                </label>
              </article>
            ))}
          </div>

          <button
            className="ghost-button"
            type="button"
            onClick={addLink}
            disabled={form.links.length >= PROJECT_LINKS_MAX_COUNT}
          >
            {copy.addLink}
          </button>
        </section>

        <div className="project-form__actions">
          <button className="ghost-button" type="button" onClick={onCancel}>
            {mode === 'create' ? copy.cancelCreate : copy.cancelEdit}
          </button>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting
              ? mode === 'create'
                ? copy.submittingCreate
                : copy.submittingEdit
              : mode === 'create'
                ? copy.submitCreate
                : copy.submitEdit}
          </button>
        </div>
      </form>
    </section>
  )
}

function isValidHttpUrl(value: string) {
  if (!value.trim()) {
    return false
  }
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}
