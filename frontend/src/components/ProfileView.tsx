import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import type { Dictionary } from '../i18n'
import type { UserProfile, UserProfilePayload } from '../types'

type ProfileViewProps = {
  copy: Dictionary['profile']
  onLoadProfile: () => Promise<UserProfile>
  onSubmit: (payload: UserProfilePayload) => Promise<void>
  onBack: () => void
}

export function ProfileView({ copy, onLoadProfile, onSubmit, onBack }: ProfileViewProps) {
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [form, setForm] = useState<UserProfile>({
    displayName: '',
    bio: '',
    email: '',
    emailPublic: false,
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false

    setLoading(true)
    setLoadError(null)

    onLoadProfile()
      .then((profile) => {
        if (cancelled) {
          return
        }
        setForm({
          displayName: profile.displayName,
          bio: profile.bio,
          email: profile.email,
          emailPublic: profile.emailPublic,
        })
        setLoading(false)
      })
      .catch((error) => {
        if (cancelled) {
          return
        }
        setLoadError(error instanceof Error ? error.message : copy.loadError)
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [copy.loadError, onLoadProfile])

  function validate(nextForm: UserProfile) {
    const errors: Record<string, string> = {}

    if (nextForm.displayName.trim().length < 2 || nextForm.displayName.trim().length > 120) {
      errors.displayName = copy.validationName
    }
    if (nextForm.bio.trim().length > 560) {
      errors.bio = copy.validationBio
    }

    return errors
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validate(form)
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      setFormError(null)
      setSuccess(null)
      return
    }

    setFieldErrors({})
    setFormError(null)
    setSuccess(null)
    setSubmitting(true)

    try {
      await onSubmit({
        displayName: form.displayName.trim(),
        bio: form.bio.trim(),
        emailPublic: form.emailPublic,
      })
      setSuccess(copy.success)
    } catch (error) {
      setFormError(error instanceof Error ? error.message : copy.saveError)
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <p className="state-card">{copy.loading}</p>
  }

  if (loadError) {
    return <p className="state-card state-card--error">{loadError}</p>
  }

  return (
    <section className="project-editor">
      <div className="project-editor__intro">
        <p className="hero__eyebrow">{copy.eyebrow}</p>
        <h1>{copy.title}</h1>
        <p className="project-editor__copy">
          {copy.copy}
        </p>
      </div>

      <form className="project-form" onSubmit={handleSubmit}>
        {success ? <p className="state-card state-card--success">{success}</p> : null}
        {formError ? <p className="auth-error">{formError}</p> : null}

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.identityEyebrow}</p>
            <h2>{copy.identityTitle}</h2>
          </div>

          <label>
            {copy.name}
            <input
              aria-label={copy.name}
              value={form.displayName}
              onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
              type="text"
              maxLength={120}
              required
            />
            {fieldErrors.displayName ? <span className="project-form__error">{fieldErrors.displayName}</span> : null}
          </label>

          <label>
            {copy.bio}
            <textarea
              aria-label={copy.bio}
              value={form.bio}
              onChange={(event) => setForm((current) => ({ ...current, bio: event.target.value }))}
              rows={5}
              maxLength={560}
            />
            {fieldErrors.bio ? <span className="project-form__error">{fieldErrors.bio}</span> : null}
          </label>
        </section>

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">{copy.contactEyebrow}</p>
            <h2>{copy.contactTitle}</h2>
          </div>

          <label>
            {copy.email}
            <input
              aria-label={copy.email}
              value={form.email}
              type="email"
              maxLength={320}
              readOnly
            />
          </label>

          <label className="checkbox-field">
            <input
              aria-label={copy.emailPublicTitle}
              checked={form.emailPublic}
              onChange={(event) => setForm((current) => ({ ...current, emailPublic: event.target.checked }))}
              type="checkbox"
            />
            <span className="checkbox-field__box" aria-hidden="true" />
            <span className="checkbox-field__copy">
              <span className="checkbox-field__title">{copy.emailPublicTitle}</span>
              <span className="checkbox-field__hint">{copy.emailPublicHint}</span>
            </span>
          </label>
        </section>

        <div className="project-form__actions">
          <button className="ghost-button" type="button" onClick={onBack}>
            {copy.back}
          </button>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? copy.submitting : copy.submit}
          </button>
        </div>
      </form>
    </section>
  )
}
