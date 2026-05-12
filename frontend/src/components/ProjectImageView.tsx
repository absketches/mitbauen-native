import { useEffect, useRef, useState } from 'react'

type ProjectImageViewProps = {
  src: string
  alt: string
  fallback: string
  className?: string
}

const MAX_IMAGE_LOAD_ATTEMPTS = 2

export function ProjectImageView({ src, alt, fallback, className }: ProjectImageViewProps) {
  const [attempt, setAttempt] = useState(0)
  const [failed, setFailed] = useState(false)
  const retryTimeoutRef = useRef<number | null>(null)

  useEffect(() => {
    if (retryTimeoutRef.current !== null) {
      window.clearTimeout(retryTimeoutRef.current)
      retryTimeoutRef.current = null
    }
    setAttempt(0)
    setFailed(false)
  }, [src])

  useEffect(() => () => {
    if (retryTimeoutRef.current !== null) {
      window.clearTimeout(retryTimeoutRef.current)
    }
  }, [])

  if (failed) {
    return (
      <div className={`${className ?? ''} project-image-fallback`.trim()} role="img" aria-label={fallback}>
        {fallback}
      </div>
    )
  }

  return (
    <img
      className={className}
      src={attempt === 0 ? src : retryUrl(src, attempt)}
      alt={alt}
      loading="lazy"
      decoding="async"
      onError={() => {
        if (attempt < MAX_IMAGE_LOAD_ATTEMPTS) {
          if (retryTimeoutRef.current !== null) {
            window.clearTimeout(retryTimeoutRef.current)
          }
          retryTimeoutRef.current = window.setTimeout(() => {
            retryTimeoutRef.current = null
            setAttempt((current) => current + 1)
          }, 250)
          return
        }
        setFailed(true)
      }}
    />
  )
}

function retryUrl(src: string, attempt: number) {
  const separator = src.includes('?') ? '&' : '?'
  return `${src}${separator}retry=${attempt}`
}
