import { useEffect, useState } from 'react'
import type { Dictionary } from '../i18n'
import type { PublicUserProfile } from '../types'

type PublicProfileViewProps = {
  copy: Dictionary['publicProfile']
  publicId: string
  onLoadProfile: (publicId: string) => Promise<PublicUserProfile>
  onBack: () => void
}

export function PublicProfileView({ copy, publicId, onLoadProfile, onBack }: PublicProfileViewProps) {
  const [profile, setProfile] = useState<PublicUserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    onLoadProfile(publicId)
      .then((nextProfile) => {
        if (cancelled) {
          return
        }
        setProfile(nextProfile)
        setLoading(false)
      })
      .catch((nextError) => {
        if (cancelled) {
          return
        }
        setError(nextError instanceof Error ? nextError.message : copy.error)
        setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [copy.error, onLoadProfile, publicId])

  if (loading) {
    return <p className="state-card">{copy.loading}</p>
  }

  if (error) {
    return <p className="state-card state-card--error">{error}</p>
  }

  if (!profile) {
    return <p className="state-card state-card--error">{copy.notFound}</p>
  }

  return (
    <section className="public-profile">
      <article className="public-profile__hero">
        <p className="hero__eyebrow">{copy.eyebrow}</p>
        <h1>{profile.displayName}</h1>
        <p className="project-editor__copy public-profile__bio">
          {profile.bio || copy.bioFallback}
        </p>
        {profile.email ? (
          <p className="public-profile__email">
            {profile.email}
          </p>
        ) : null}
      </article>

      <div className="project-form__actions">
        <button className="ghost-button" type="button" onClick={onBack}>
          {copy.back}
        </button>
      </div>
    </section>
  )
}
