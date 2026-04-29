import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import type { OpenRole, ProjectDetails, ProjectPayload } from '../types'

type ProjectFormViewProps = {
  mode: 'create' | 'edit'
  slug?: string
  sessionUserId?: number
  loadProject?: (slug: string) => Promise<ProjectDetails>
  onSubmit: (payload: ProjectPayload) => Promise<void>
  onCancel: () => void
}

type ProjectFormState = {
  title: string
  description: string
  founderRole: string
  founderCommitment: string
  openRoles: OpenRole[]
}

const BLANK_ROLE: OpenRole = { title: '', commitment: '' }

export function ProjectFormView({
  mode,
  slug,
  sessionUserId,
  loadProject,
  onSubmit,
  onCancel,
}: ProjectFormViewProps) {
  const [loadingProject, setLoadingProject] = useState(mode === 'edit')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loadedProject, setLoadedProject] = useState<ProjectDetails | null>(null)
  const [form, setForm] = useState<ProjectFormState>({
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
      setLoadingProject(false)
      return
    }

    setLoadingProject(true)
    setLoadError(null)

    loadProject(slug)
      .then((project) => {
        if (cancelled) {
          return
        }
        setLoadedProject(project)
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
        setLoadError('We could not load this project right now.')
        setLoadingProject(false)
      })

    return () => {
      cancelled = true
    }
  }, [loadProject, mode, slug])

  function updateField(field: keyof Omit<ProjectFormState, 'openRoles'>, value: string) {
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

  function validate(nextForm: ProjectFormState) {
    const errors: Record<string, string> = {}

    if (nextForm.title.trim().length < 5 || nextForm.title.trim().length > 120) {
      errors.title = 'Use a project title between 5 and 120 characters.'
    }
    if (nextForm.description.trim().length < 40 || nextForm.description.trim().length > 1024) {
      errors.description = 'Use a project description between 40 and 1024 characters.'
    }
    if (nextForm.founderRole.trim().length < 3 || nextForm.founderRole.trim().length > 80) {
      errors.founderRole = 'Use a founder role between 3 and 80 characters.'
    }
    if (nextForm.founderCommitment.trim().length < 10 || nextForm.founderCommitment.trim().length > 280) {
      errors.founderCommitment = 'Use a founder commitment between 10 and 280 characters.'
    }
    if (nextForm.openRoles.length < 1) {
      errors.openRoles = 'Add at least one open role.'
    }
    if (nextForm.openRoles.length > 6) {
      errors.openRoles = 'You can add up to 6 open roles.'
    }

    nextForm.openRoles.forEach((role, index) => {
      if (role.title.trim().length < 3 || role.title.trim().length > 80) {
        errors[`openRoleTitle_${index}`] = 'Use a role title between 3 and 80 characters.'
      }
      if (role.commitment.trim().length < 3 || role.commitment.trim().length > 80) {
        errors[`openRoleCommitment_${index}`] = 'Use a role commitment between 3 and 80 characters.'
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
      setFormError(error instanceof Error ? error.message : 'We could not save this project right now.')
      setSubmitting(false)
      return
    }

    setSubmitting(false)
  }

  if (loadingProject) {
    return <p className="state-card">Loading project...</p>
  }

  if (loadError) {
    return <p className="state-card state-card--error">{loadError}</p>
  }

  if (mode === 'edit' && loadedProject && loadedProject.ownerUserId !== sessionUserId) {
    return (
      <section className="state-shell">
        <article className="state-card state-card--error">
          Only the project owner can edit this project.
        </article>
        <button className="ghost-button" type="button" onClick={onCancel}>
          Back to project
        </button>
      </section>
    )
  }

  return (
    <section className="project-editor">
      <div className="project-editor__intro">
        <p className="hero__eyebrow">{mode === 'create' ? 'Create project' : 'Edit project'}</p>
        <h1>{mode === 'create' ? 'Post a project that shows real momentum.' : 'Refine the project story people will join.'}</h1>
        <p className="project-editor__copy">
          Lead with what you are actually building, what you are personally committing, and the roles that would make
          the project meaningfully stronger.
        </p>
      </div>

      <form className="project-form" onSubmit={handleSubmit}>
        {formError ? <p className="auth-error">{formError}</p> : null}

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">Idea</p>
            <h2>Frame the project clearly.</h2>
          </div>

          <label>
            Title
            <input
              aria-label="Project title"
              value={form.title}
              onChange={(event) => updateField('title', event.target.value)}
              type="text"
              maxLength={120}
              required
            />
            {fieldErrors.title ? <span className="project-form__error">{fieldErrors.title}</span> : null}
          </label>

          <label>
            Description
            <textarea
              aria-label="Project description"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
              rows={7}
              maxLength={1024}
              required
            />
            {fieldErrors.description ? <span className="project-form__error">{fieldErrors.description}</span> : null}
          </label>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">Founder commitment</p>
            <h2>Show how you are leading from the front.</h2>
          </div>

          <label>
            Your role in this project
            <input
              aria-label="Your role in this project"
              value={form.founderRole}
              onChange={(event) => updateField('founderRole', event.target.value)}
              type="text"
              maxLength={80}
              required
            />
            {fieldErrors.founderRole ? <span className="project-form__error">{fieldErrors.founderRole}</span> : null}
          </label>

          <label>
            What you are personally committing
            <textarea
              aria-label="What you are personally committing"
              value={form.founderCommitment}
              onChange={(event) => updateField('founderCommitment', event.target.value)}
              rows={5}
              maxLength={280}
              required
            />
            {fieldErrors.founderCommitment ? (
              <span className="project-form__error">{fieldErrors.founderCommitment}</span>
            ) : null}
          </label>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">Open roles</p>
            <h2>Make it obvious where others can lean in.</h2>
          </div>

          {fieldErrors.openRoles ? <p className="project-form__error">{fieldErrors.openRoles}</p> : null}

          <div className="project-form__roles">
            {form.openRoles.map((role, index) => (
              <article className="project-form__role-card" key={`${index}-${mode}`}>
                <div className="project-form__role-header">
                  <strong>Role {index + 1}</strong>
                  {form.openRoles.length > 1 ? (
                    <button className="ghost-button ghost-button--small" type="button" onClick={() => removeRole(index)}>
                      Remove
                    </button>
                  ) : null}
                </div>

                <label>
                  Role title
                  <input
                    aria-label={`Role title ${index + 1}`}
                    value={role.title}
                    onChange={(event) => updateRole(index, 'title', event.target.value)}
                    type="text"
                    maxLength={80}
                    required
                  />
                  {fieldErrors[`openRoleTitle_${index}`] ? (
                    <span className="project-form__error">{fieldErrors[`openRoleTitle_${index}`]}</span>
                  ) : null}
                </label>

                <label>
                  Role commitment
                  <textarea
                    aria-label={`Role commitment ${index + 1}`}
                    value={role.commitment}
                    onChange={(event) => updateRole(index, 'commitment', event.target.value)}
                    rows={3}
                    maxLength={80}
                    required
                  />
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
            Add another role
          </button>
        </section>

        <div className="project-form__actions">
          <button className="ghost-button" type="button" onClick={onCancel}>
            Cancel
          </button>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting
              ? mode === 'create'
                ? 'Creating project...'
                : 'Saving changes...'
              : mode === 'create'
                ? 'Create project'
                : 'Save changes'}
          </button>
        </div>
      </form>
    </section>
  )
}
