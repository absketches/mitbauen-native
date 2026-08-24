import type { Language } from './i18n'
import type { Project, ProjectDescriptions } from './types'

export type DisplayDescription = {
  text: string
  translated: boolean
  originalLanguage: Language | null
}

export function descriptionForLanguage(project: Pick<Project, 'descriptions'>, language: Language) {
  return descriptionDisplayForLanguage(project, language).text
}

export function descriptionDisplayForLanguage(
  project: Pick<Project, 'descriptions' | 'descriptionViews'>,
  language: Language,
): DisplayDescription {
  const view = project.descriptionViews?.[language]
  if (view?.text) {
    return {
      text: view.text,
      translated: view.translated,
      originalLanguage: view.originalLanguage,
    }
  }

  return {
    text: project.descriptions[language] ?? '',
    translated: false,
    originalLanguage: project.descriptions[language] ? language : null,
  }
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
