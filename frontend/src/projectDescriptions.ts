import type { Language } from './i18n'
import type { Project, ProjectDescriptions } from './types'

export function descriptionForLanguage(project: Pick<Project, 'descriptions'>, language: Language) {
  return project.descriptions[language] ?? ''
}

export function normalizeDescriptions(descriptions: ProjectDescriptions): ProjectDescriptions {
  return {
    de: blankToNull(descriptions.de),
    en: blankToNull(descriptions.en),
  }
}

function blankToNull(value: string | null) {
  const trimmed = value?.trim() ?? ''
  return trimmed.length > 0 ? trimmed : null
}
