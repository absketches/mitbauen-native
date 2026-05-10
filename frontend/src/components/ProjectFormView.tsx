import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { ApiError } from '../api'
import type { Dictionary } from '../i18n'
import type { OpenRole, ProjectDetails, ProjectPayload } from '../types'

type ProjectFormViewProps = {
  copy: Dictionary['projectForm']
  mode: 'create' | 'edit'
  slug?: string
  loadProject?: (slug: string) => Promise<ProjectDetails>
  onSubmit: (payload: ProjectPayload) => Promise<void>
  onCancel: () => void
}

const BLANK_ROLE: OpenRole = { title: '', commitment: '' }
const PROJECT_TITLE_MAX_LENGTH = 120
const PROJECT_DESCRIPTION_MAX_LENGTH = 3000
const FOUNDER_ROLE_MAX_LENGTH = 120
const FOUNDER_COMMITMENT_MAX_LENGTH = 500
const OPEN_ROLE_TITLE_MAX_LENGTH = 120
const OPEN_ROLE_COMMITMENT_MAX_LENGTH = 500

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
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

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
        })
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

  function updateField(field: keyof Omit<ProjectPayload, 'openRoles'>, value: string) {
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

    nextForm.openRoles.forEach((role, index) => {
      if (role.title.trim().length < 3 || role.title.trim().length > OPEN_ROLE_TITLE_MAX_LENGTH) {
        errors[`openRoleTitle_${index}`] = copy.validationRoleTitle
      }
      if (role.commitment.trim().length < 3 || role.commitment.trim().length > OPEN_ROLE_COMMITMENT_MAX_LENGTH) {
        errors[`openRoleCommitment_${index}`] = copy.validationRoleCommitment
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
            {fieldErrors.description ? <span className="project-form__error">{fieldErrors.description}</span> : null}
          </label>
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
