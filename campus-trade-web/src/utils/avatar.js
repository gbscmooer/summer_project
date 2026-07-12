/**
 * 头像工具：优先用用户自定义 URL；外链失败时用本地 SVG data URI（不依赖第三方 CDN）。
 */

export function isProbablyBrokenCdn(url) {
  if (!url) return true
  const u = String(url)
  return u.includes('dicebear.com') || u.includes('api.dicebear')
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

/** 展示用头像：有自定义非 CDN URL 时用原图，否则本地字母头像。 */
export function resolveAvatarSrc(avatar, name) {
  if (avatar && !isProbablyBrokenCdn(avatar)) {
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
