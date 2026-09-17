export type MarkdownAction =
  | 'heading'
  | 'bold'
  | 'italic'
  | 'quote'
  | 'unordered-list'
  | 'ordered-list'
  | 'link'
  | 'code'

export interface EditorSelection {
  value: string
  selectionStart: number
  selectionEnd: number
}

export interface OutlineItem {
  level: number
  text: string
  line: number
}

function replaceRange(
  value: string,
  start: number,
  end: number,
  replacement: string,
  selectedOffset = 0,
  selectedLength = 0,
): EditorSelection {
  const nextValue = value.slice(0, start) + replacement + value.slice(end)
  const selectionStart = start + selectedOffset
  return {
    value: nextValue,
    selectionStart,
    selectionEnd: selectionStart + selectedLength,
  }
}

function wrapSelection(
  value: string,
  start: number,
  end: number,
  prefix: string,
  suffix = prefix,
  placeholder = '文字',
): EditorSelection {
  const selected = value.slice(start, end) || placeholder
  return replaceRange(
    value,
    start,
    end,
    `${prefix}${selected}${suffix}`,
    prefix.length,
    selected.length,
  )
}

function prefixLines(
  value: string,
  start: number,
  end: number,
  createPrefix: (index: number) => string,
): EditorSelection {
  const lineStart = value.lastIndexOf('\n', start - 1) + 1
  const lineEnd = value.indexOf('\n', end)
  const actualEnd = lineEnd === -1 ? value.length : lineEnd
  const lines = value.slice(lineStart, actualEnd).split('\n')
  const replacement = lines.map((line, index) => `${createPrefix(index)}${line}`).join('\n')
  return replaceRange(value, lineStart, actualEnd, replacement, replacement.length, 0)
}

export function insertMarkdown(
  value: string,
  start: number,
  end: number,
  action: MarkdownAction,
): EditorSelection {
  if (action === 'bold') return wrapSelection(value, start, end, '**')
  if (action === 'italic') return wrapSelection(value, start, end, '*')
  if (action === 'code') return wrapSelection(value, start, end, '`', '`', '代码')
  if (action === 'link') {
    return wrapSelection(value, start, end, '[', '](https://)', '链接文字')
  }
  if (action === 'quote') return prefixLines(value, start, end, () => '> ')
  if (action === 'unordered-list') return prefixLines(value, start, end, () => '- ')
  if (action === 'ordered-list') return prefixLines(value, start, end, (index) => `${index + 1}. `)
  if (action === 'heading') {
    const lineStart = value.lastIndexOf('\n', start - 1) + 1
    return replaceRange(value, lineStart, lineStart, '## ', 3, 0)
  }
  return { value, selectionStart: start, selectionEnd: end }
}

export function parseOutline(markdown: string): OutlineItem[] {
  const outline: OutlineItem[] = []
  let inFence = false

  markdown.split('\n').forEach((line, index) => {
    if (/^\s*```/.test(line)) {
      inFence = !inFence
      return
    }
    if (inFence) return

    const match = /^(#{1,6})\s+(.+?)\s*$/.exec(line)
    if (!match) return

    const level = match[1]?.length
    const text = match[2]
    if (!level || !text) return

    outline.push({
      level,
      text,
      line: index + 1,
    })
  })

  return outline
}

export function countEditorStats(value: string, cursor: number) {
  const beforeCursor = value.slice(0, cursor)
  const lines = beforeCursor.split('\n')

  return {
    characters: Array.from(value.replace(/\s/g, '')).length,
    line: lines.length,
    column: Array.from(lines.at(-1) ?? '').length + 1,
  }
}
