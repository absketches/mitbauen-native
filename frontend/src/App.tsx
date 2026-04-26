import { useEffect, useState } from 'react'
import { loadProjects } from './api'
import { ProjectCard } from './components/ProjectCard'
import type { Project } from './types'

type AppProps = {
  fetchProjects?: () => Promise<Project[]>
}

export default function App({ fetchProjects = loadProjects }: AppProps) {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    fetchProjects()
      .then((nextProjects) => {
        if (!cancelled) {
          setProjects(nextProjects)
          setLoading(false)
        }
      })
      .catch((nextError: Error) => {
        if (!cancelled) {
          setError(nextError.message)
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [fetchProjects])

  return (
    <main className="page-shell">
      <section className="hero">
        <p className="hero__eyebrow">Mitbauen Project Feed</p>
        <h1>Projects that already have founder energy behind them.</h1>
        <p className="hero__copy">
          The first slice is intentionally public and read-only: people can browse real projects, see founder
          commitment, and understand where help is needed.
        </p>
      </section>

      {loading ? <p className="state-card">Loading projects...</p> : null}
      {error ? <p className="state-card state-card--error">{error}</p> : null}

      {!loading && !error ? (
        <section className="feed-grid" aria-label="Project feed">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </section>
      ) : null}
    </main>
  )
}
