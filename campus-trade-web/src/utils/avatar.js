/**
 * 头像工具：优先用站内上传路径；外链 / dicebear 等不安全 URL 用本地字母头像。
 */

import { isSafeAvatarUrl } from '@/utils/validateImage'

export function isProbablyBrokenCdn(url) {
  if (!url) return true
  const u = String(url)
  return u.includes('dicebear.com') || u.includes('api.dicebear')
}

/** 是否可作为 <img src> 的可信头像（仅站内上传路径） */
export function isTrustedAvatarUrl(url) {
  return isSafeAvatarUrl(url)
}

/** 由昵称/用户名生成稳定色相的字母头像（SVG data URI）。 */
export function letterAvatarDataUri(name, size = 128) {
  const label = String(name || '?').trim() || '?'
  const letter = Array.from(label)[0].toUpperCase()
  const hue = hashHue(label)
  const bg = `hsl(${hue} 42% 42%)`
  const fg = '#ffffff'
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">` +
    `<rect width="100%" height="100%" fill="${bg}"/>` +
    `<text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" ` +
    `font-family="system-ui,Segoe UI,sans-serif" font-size="${Math.round(size * 0.42)}" font-weight="600" fill="${fg}">` +
    `${escapeXml(letter)}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/**
 * 展示用头像：仅站内上传路径用原图；legacy 外链 / dicebear 一律回退字母头像（防注入展示）。
 */
export function resolveAvatarSrc(avatar, name) {
  if (isTrustedAvatarUrl(avatar)) {
    return avatar
  }
  return letterAvatarDataUri(name)
}

function hashHue(text) {
  let h = 0
  for (let i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) >>> 0
  }
  return h % 360
}

function escapeXml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
