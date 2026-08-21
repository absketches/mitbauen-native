import type { FormEvent } from 'react'
import { useState } from 'react'
import { ApiError } from '../api'
import type { JobApplicationPayload } from '../types'

type RoleApplicationCopy = {
  applyFitLabel: string
  applyFitPlaceholder: string
  applyAvailabilityLabel: string
  applyAvailabilityPlaceholder: string
  applySubmit: string
  applySubmitting: string
  applyCancel: string
  applySuccess: string
  applyError: string
  applyDuplicateError: string
  applyFitValidation: string
}

type RoleApplicationFormProps = {
  copy: RoleApplicationCopy
  roleId: number
  onSubmit: (payload: JobApplicationPayload) => Promise<void>
  onCancel: () => void
}

export function RoleApplicationForm({ copy, roleId, onSubmit, onCancel }: RoleApplicationFormProps) {
  const [fit, setFit] = useState('')
  const [availability, setAvailability] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextFit = fit.trim()
    const nextAvailability = availability.trim()
    if (nextFit.length < 20) {
      setError(copy.applyFitValidation)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({ roleId, fit: nextFit, availability: nextAvailability })
      setSent(true)
      setFit('')
      setAvailability('')
    } catch (nextError) {
      setError(nextError instanceof ApiError && nextError.code === 'JOB_APPLICATION_DUPLICATE' ? copy.applyDuplicateError : copy.applyError)
    } finally {
      setSubmitting(false)
    }
  }

  if (sent) {
    return (
      <div className="role-application-form role-application-form--sent">
        <p>{copy.applySuccess}</p>
        <button className="ghost-button ghost-button--small" type="button" onClick={onCancel}>
          {copy.applyCancel}
        </button>
      </div>
    )
  }

  return (
    <form className="role-application-form" onSubmit={handleSubmit}>
      <label>
        <span>{copy.applyFitLabel}</span>
        <textarea
          value={fit}
          onChange={(event) => setFit(event.target.value)}
          placeholder={copy.applyFitPlaceholder}
          maxLength={2000}
          rows={4}
          required
        />
      </label>
      <label>
        <span>{copy.applyAvailabilityLabel}</span>
        <textarea
          value={availability}
          onChange={(event) => setAvailability(event.target.value)}
          placeholder={copy.applyAvailabilityPlaceholder}
          maxLength={500}
          rows={2}
        />
      </label>
      {error ? <p className="auth-error">{error}</p> : null}
      <div className="role-application-form__actions">
        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? copy.applySubmitting : copy.applySubmit}
        </button>
        <button className="ghost-button" type="button" onClick={onCancel} disabled={submitting}>
          {copy.applyCancel}
        </button>
      </div>
    </form>
  )
}
