import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import type { UserProfile, UserProfilePayload } from '../types'

type ProfileViewProps = {
  onLoadProfile: () => Promise<UserProfile>
  onSubmit: (payload: UserProfilePayload) => Promise<void>
  onBack: () => void
}

type ProfileFormState = {
  displayName: string
  bio: string
  email: string
  emailPublic: boolean
}

export function ProfileView({ onLoadProfile, onSubmit, onBack }: ProfileViewProps) {
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [form, setForm] = useState<ProfileFormState>({
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
        setLoadError(error instanceof Error ? error.message : 'We could not load your profile right now.')
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [onLoadProfile])

  function validate(nextForm: ProfileFormState) {
    const errors: Record<string, string> = {}

    if (nextForm.displayName.trim().length < 2 || nextForm.displayName.trim().length > 120) {
      errors.displayName = 'Use a display name between 2 and 120 characters.'
    }
    if (nextForm.bio.trim().length > 560) {
      errors.bio = 'Keep your bio to 560 characters or fewer.'
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
      setSuccess('Your profile is updated.')
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'We could not save your profile right now.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <p className="state-card">Loading profile...</p>
  }

  if (loadError) {
    return <p className="state-card state-card--error">{loadError}</p>
  }

  return (
    <section className="project-editor">
      <div className="project-editor__intro">
        <p className="hero__eyebrow">Profile</p>
        <h1>Shape how other builders recognize you.</h1>
        <p className="project-editor__copy">
          Keep your name current, add a short bio, and decide whether collaborators should be able to see your email.
        </p>
      </div>

      <form className="project-form" onSubmit={handleSubmit}>
        {success ? <p className="state-card state-card--success">{success}</p> : null}
        {formError ? <p className="auth-error">{formError}</p> : null}

        <section className="project-form__section">
          <div className="project-form__section-header">
            <p className="hero__eyebrow">Identity</p>
            <h2>Update the basics.</h2>
          </div>

          <label>
            Name
            <input
              aria-label="Name"
              value={form.displayName}
              onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
              type="text"
              maxLength={120}
              required
            />
            {fieldErrors.displayName ? <span className="project-form__error">{fieldErrors.displayName}</span> : null}
          </label>

          <label>
            Bio
            <textarea
              aria-label="Bio"
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
            <p className="hero__eyebrow">Contact</p>
            <h2>Control how reachable you are.</h2>
          </div>

          <label>
            Email
            <input
              aria-label="Email"
              value={form.email}
              type="email"
              maxLength={320}
              readOnly
            />
          </label>

          <label className="checkbox-field">
            <input
              aria-label="Make my email public"
              checked={form.emailPublic}
              onChange={(event) => setForm((current) => ({ ...current, emailPublic: event.target.checked }))}
              type="checkbox"
            />
            <span className="checkbox-field__box" aria-hidden="true" />
            <span className="checkbox-field__copy">
              <span className="checkbox-field__title">Make my email public</span>
              <span className="checkbox-field__hint">Show this on your profile so collaborators can reach you.</span>
            </span>
          </label>
        </section>

        <div className="project-form__actions">
          <button className="ghost-button" type="button" onClick={onBack}>
            Back to projects
          </button>
          <button className="primary-button" type="submit" disabled={submitting}>
            {submitting ? 'Saving profile...' : 'Save profile'}
          </button>
        </div>
      </form>
    </section>
  )
}
