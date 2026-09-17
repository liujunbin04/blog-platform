import { describe, expect, it } from 'vitest'

import {
  countEditorStats,
  insertMarkdown,
  parseOutline,
  type MarkdownAction,
} from './editor'

describe('insertMarkdown', () => {
  it('wraps selected text for bold', () => {
    expect(insertMarkdown('hello world', 6, 11, 'bold')).toEqual({
      value: 'hello **world**',
      selectionStart: 8,
      selectionEnd: 13,
    })
  })

  it('inserts heading at the current line start', () => {
    expect(insertMarkdown('标题', 2, 2, 'heading')).toEqual({
      value: '## 标题',
      selectionStart: 3,
      selectionEnd: 3,
    })
  })

  it('inserts a link placeholder when there is no selection', () => {
    expect(insertMarkdown('', 0, 0, 'link')).toEqual({
      value: '[链接文字](https://)',
      selectionStart: 1,
      selectionEnd: 5,
    })
  })

  it('prefixes each selected line for quote', () => {
    expect(insertMarkdown('第一行\n第二行', 0, 7, 'quote')).toEqual({
      value: '> 第一行\n> 第二行',
      selectionStart: 11,
      selectionEnd: 11,
    })
  })
})

describe('parseOutline', () => {
  it('parses ATX headings and ignores fenced code blocks', () => {
    const markdown = [
      '# 一级标题',
      '',
      '```md',
      '# 代码里的标题',
      '```',
      '',
      '## 二级标题',
      '### 三级标题',
    ].join('\n')

    expect(parseOutline(markdown)).toEqual([
      { level: 1, text: '一级标题', line: 1 },
      { level: 2, text: '二级标题', line: 7 },
      { level: 3, text: '三级标题', line: 8 },
    ])
  })
})

describe('countEditorStats', () => {
  it('counts non-whitespace Unicode characters and cursor position', () => {
    expect(countEditorStats('你好 世界\n第二行', 7)).toEqual({
      characters: 7,
      line: 2,
      column: 2,
    })
  })
})

describe('MarkdownAction type', () => {
  it('accepts all supported editor actions', () => {
    const actions: MarkdownAction[] = [
      'heading',
      'bold',
      'italic',
      'quote',
      'unordered-list',
      'ordered-list',
      'link',
      'code',
    ]
    expect(actions).toHaveLength(8)
  })
})
