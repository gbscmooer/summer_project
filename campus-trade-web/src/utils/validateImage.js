/** 封面/头像等站内图片上传前的客户端校验（类型、大小、魔数、可解码尺寸） */

export const COVER_MAX_BYTES = 2 * 1024 * 1024
export const COVER_MAX_DIMENSION = 8192
export const COVER_MAX_PIXELS = 10_000_000
export const COVER_ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const COVER_URL_PREFIX = '/api/product/image/'

const TYPE_BY_EXT = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp'
}

export function isSafeCoverUrl(url) {
  if (!url) return false
  return /^\/api\/product\/image\/[a-f0-9]{32}\.(jpg|png|webp)$/i.test(String(url).trim())
}

function normalizeType(type) {
  return String(type || '')
    .toLowerCase()
    .split(';')[0]
    .trim()
}

function hasValidSignature(contentType, header) {
  if (!header || header.length < 3) return false
  if (contentType === 'image/jpeg') {
    return header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff
  }
  if (contentType === 'image/png') {
    return (
      header.length >= 8 &&
      header[0] === 0x89 &&
      header[1] === 0x50 &&
      header[2] === 0x4e &&
      header[3] === 0x47 &&
      header[4] === 0x0d &&
      header[5] === 0x0a &&
      header[6] === 0x1a &&
      header[7] === 0x0a
    )
  }
  if (contentType === 'image/webp') {
    return (
      header.length >= 12 &&
      header[0] === 0x52 &&
      header[1] === 0x49 &&
      header[2] === 0x46 &&
      header[3] === 0x46 &&
      header[8] === 0x57 &&
      header[9] === 0x45 &&
      header[10] === 0x42 &&
      header[11] === 0x50
    )
  }
  return false
}

async function readHeader(file) {
  const buf = await file.slice(0, 12).arrayBuffer()
  return new Uint8Array(buf)
}

async function readImageSize(file) {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(file)
    try {
      return { width: bitmap.width, height: bitmap.height }
    } finally {
      bitmap.close()
    }
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve({ width: img.naturalWidth, height: img.naturalHeight })
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('decode'))
    }
    img.src = url
  })
}

/**
 * @returns {Promise<{ ok: true, contentType: string } | { ok: false, reason: string }>}
 * reason: type | size | signature | decode | dimension
 */
export async function validateCoverImageFile(file, options = {}) {
  const maxBytes = options.maxBytes ?? COVER_MAX_BYTES
  if (!file) return { ok: false, reason: 'type' }

  let contentType = normalizeType(file.type)
  if (!COVER_ALLOWED_TYPES.includes(contentType)) {
    const name = String(file.name || '').toLowerCase()
    const ext = Object.keys(TYPE_BY_EXT).find((e) => name.endsWith(e))
    contentType = ext ? TYPE_BY_EXT[ext] : ''
  }
  if (!COVER_ALLOWED_TYPES.includes(contentType)) {
    return { ok: false, reason: 'type' }
  }
  if (file.size <= 0 || file.size > maxBytes) {
    return { ok: false, reason: 'size' }
  }

  try {
    const header = await readHeader(file)
    if (!hasValidSignature(contentType, header)) {
      return { ok: false, reason: 'signature' }
    }
  } catch {
    return { ok: false, reason: 'signature' }
  }

  try {
    const { width, height } = await readImageSize(file)
    const pixels = width * height
    if (
      width <= 0 ||
      height <= 0 ||
      width > COVER_MAX_DIMENSION ||
      height > COVER_MAX_DIMENSION ||
      pixels > COVER_MAX_PIXELS
    ) {
      return { ok: false, reason: 'dimension' }
    }
  } catch {
    return { ok: false, reason: 'decode' }
  }

  return { ok: true, contentType }
}
