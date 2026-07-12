/**
 * 轻量 HTML 白名单清洗，用于话题富文本展示，避免 XSS。
 */
const ALLOWED_TAGS = new Set([
  'P', 'BR', 'DIV', 'SPAN', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'STRIKE',
  'H1', 'H2', 'H3', 'H4', 'BLOCKQUOTE', 'UL', 'OL', 'LI', 'A', 'IMG',
  'VIDEO', 'SOURCE', 'HR', 'PRE', 'CODE', 'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD',
  'SUP', 'SUB'
])

const ALLOWED_ATTRS = {
  A: new Set(['href', 'title', 'target', 'rel']),
  IMG: new Set(['src', 'alt', 'title']),
  VIDEO: new Set(['src', 'controls', 'poster', 'preload']),
  SOURCE: new Set(['src', 'type']),
  TD: new Set(['colspan', 'rowspan']),
  TH: new Set(['colspan', 'rowspan']),
  '*': new Set(['class'])
}

function isSafeUrl(url) {
  if (!url) return false
  const value = String(url).trim()
  if (!value) return false
  const lower = value.toLowerCase()
  if (lower.startsWith('javascript:') || lower.startsWith('data:text') || lower.startsWith('vbscript:')) {
    return false
  }
  // 允许相对路径、同站 api 图片、http(s)
  return (
    value.startsWith('/') ||
    value.startsWith('./') ||
    lower.startsWith('http://') ||
    lower.startsWith('https://') ||
    lower.startsWith('blob:') ||
    lower.startsWith('data:image/')
  )
}

function cleanNode(node) {
  if (node.nodeType === Node.TEXT_NODE) {
    return document.createTextNode(node.textContent || '')
  }
  if (node.nodeType !== Node.ELEMENT_NODE) {
    return null
  }

  const tag = node.tagName.toUpperCase()
  if (!ALLOWED_TAGS.has(tag)) {
    const frag = document.createDocumentFragment()
    Array.from(node.childNodes).forEach((child) => {
      const cleaned = cleanNode(child)
      if (cleaned) frag.appendChild(cleaned)
    })
    return frag
  }

  const el = document.createElement(tag.toLowerCase())
  const allowed = new Set([
    ...(ALLOWED_ATTRS['*'] || []),
    ...(ALLOWED_ATTRS[tag] || [])
  ])

  Array.from(node.attributes || []).forEach((attr) => {
    const name = attr.name.toLowerCase()
    if (!allowed.has(name) && !allowed.has(attr.name)) return
    if (name === 'href' || name === 'src') {
      if (!isSafeUrl(attr.value)) return
      el.setAttribute(name, attr.value)
      if (name === 'href') {
        el.setAttribute('rel', 'noopener noreferrer')
        if (!el.getAttribute('target')) el.setAttribute('target', '_blank')
      }
      return
    }
    if (name.startsWith('on')) return
    el.setAttribute(name, attr.value)
  })

  Array.from(node.childNodes).forEach((child) => {
    const cleaned = cleanNode(child)
    if (cleaned) el.appendChild(cleaned)
  })
  return el
}

export function sanitizeHtml(html) {
  if (!html || typeof html !== 'string') return ''
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div id="__root">${html}</div>`, 'text/html')
  const root = doc.getElementById('__root')
  if (!root) return ''

  const out = document.createElement('div')
  Array.from(root.childNodes).forEach((child) => {
    const cleaned = cleanNode(child)
    if (cleaned) out.appendChild(cleaned)
  })
  return out.innerHTML
}

/** 列表预览：去掉标签取纯文本 */
export function htmlToPlainText(html) {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = sanitizeHtml(html)
  return (div.textContent || '').replace(/\s+/g, ' ').trim()
}

export function isRichContentEmpty(html) {
  return !htmlToPlainText(html)
}
