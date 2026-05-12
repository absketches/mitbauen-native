import type { ReactNode } from 'react'

type SafeMarkdownProps = {
  text: string
  className?: string
}

export function SafeMarkdown({ text, className }: SafeMarkdownProps) {
  const blocks = parseBlocks(text)

  return (
    <div className={`markdown-content${className ? ` ${className}` : ''}`}>
      {blocks.map((block, index) => {
        if (block.type === 'list') {
          return (
            <ul key={index}>
              {block.items.map((item, itemIndex) => (
                <li key={itemIndex}>{parseInline(item)}</li>
              ))}
            </ul>
          )
        }

        return <p key={index}>{renderParagraph(block.lines)}</p>
      })}
    </div>
  )
}

export function markdownPreview(text: string, maxLength = 210) {
  const plainText = text
    .replace(/\[([^\]]+)]\(([^)]+)\)/g, '$1')
    .replace(/[*_`>#-]/g, '')
    .replace(/\s+/g, ' ')
    .trim()

  return plainText.length > maxLength ? `${plainText.slice(0, maxLength - 3).trimEnd()}...` : plainText
}

type MarkdownBlock =
  | { type: 'paragraph'; lines: string[] }
  | { type: 'list'; items: string[] }

function parseBlocks(text: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = []
  const lines = text.replace(/\r\n/g, '\n').split('\n')
  let paragraph: string[] = []
  let list: string[] = []

  function flushParagraph() {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', lines: paragraph })
      paragraph = []
    }
  }

  function flushList() {
    if (list.length > 0) {
      blocks.push({ type: 'list', items: list })
      list = []
    }
  }

  for (const line of lines) {
    const trimmed = line.trim()
    const listMatch = trimmed.match(/^[-*]\s+(.+)$/)

    if (!trimmed) {
      flushParagraph()
      flushList()
      continue
    }

    if (listMatch) {
      flushParagraph()
      list.push(listMatch[1])
      continue
    }

    flushList()
    paragraph.push(trimmed)
  }

  flushParagraph()
  flushList()

  return blocks
}

function renderParagraph(lines: string[]) {
  return lines.flatMap((line, index) => {
    const content = parseInline(line)
    return index === 0 ? content : [<br key={`br-${index}`} />, ...content]
  })
}

function parseInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let index = 0

  function pushText(until: number) {
    if (until > index) {
      nodes.push(text.slice(index, until))
      index = until
    }
  }

  while (index < text.length) {
    const linkStart = text.indexOf('[', index)
    const boldStart = text.indexOf('**', index)
    const italicStart = text.indexOf('*', index)
    const candidates = [linkStart, boldStart, italicStart].filter((candidate) => candidate >= 0)
    const next = Math.min(...candidates)

    if (!Number.isFinite(next)) {
      nodes.push(text.slice(index))
      break
    }

    pushText(next)

    if (text.startsWith('[', index)) {
      const labelEnd = text.indexOf(']', index + 1)
      const urlStart = labelEnd >= 0 && text[labelEnd + 1] === '(' ? labelEnd + 2 : -1
      const urlEnd = urlStart >= 0 ? text.indexOf(')', urlStart) : -1
      if (labelEnd > index && urlStart > 0 && urlEnd > urlStart) {
        const label = text.slice(index + 1, labelEnd)
        const url = text.slice(urlStart, urlEnd).trim()
        const safeUrl = safeLink(url)
        if (safeUrl) {
          nodes.push(
            <a href={safeUrl} key={`link-${index}`} rel="noopener noreferrer" target="_blank">
              {parseInline(label)}
            </a>,
          )
          index = urlEnd + 1
          continue
        }
      }
    }

    if (text.startsWith('**', index)) {
      const end = text.indexOf('**', index + 2)
      if (end > index + 2) {
        nodes.push(<strong key={`strong-${index}`}>{parseInline(text.slice(index + 2, end))}</strong>)
        index = end + 2
        continue
      }
    }

    if (text.startsWith('*', index) && !text.startsWith('**', index)) {
      const end = text.indexOf('*', index + 1)
      if (end > index + 1) {
        nodes.push(<em key={`em-${index}`}>{parseInline(text.slice(index + 1, end))}</em>)
        index = end + 1
        continue
      }
    }

    nodes.push(text[index])
    index += 1
  }

  return nodes
}

function safeLink(url: string) {
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' || parsed.protocol === 'mailto:' ? parsed.href : null
  } catch {
    return null
  }
}
