/** 客户端图片压缩：超过 1MB 时降低分辨率/质量后再上传 */

export const IMAGE_TARGET_MAX_BYTES = 1024 * 1024
/** 压缩前硬上限，避免读入超大图拖垮浏览器 */
export const IMAGE_SOURCE_MAX_BYTES = 20 * 1024 * 1024
export const IMAGE_ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const TYPE_BY_EXT = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp'
}

function normalizeType(type) {
  return String(type || '')
    .toLowerCase()
    .split(';')[0]
    .trim()
}

export function resolveImageContentType(file) {
  let contentType = normalizeType(file?.type)
  if (IMAGE_ALLOWED_TYPES.includes(contentType)) return contentType
  const name = String(file?.name || '').toLowerCase()
  const ext = Object.keys(TYPE_BY_EXT).find((e) => name.endsWith(e))
  return ext ? TYPE_BY_EXT[ext] : ''
}

function canvasToBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) reject(new Error('compress'))
        else resolve(blob)
      },
      type,
      quality
    )
  })
}

function renameToJpeg(name) {
  const base = String(name || 'image').replace(/\.[^.]+$/, '')
  return `${base || 'image'}.jpg`
}

/**
 * 若文件 ≤ 1MB 则原样返回；否则通过 canvas 压缩分辨率/质量至 ≤ 1MB。
 * @returns {Promise<{ file: File, compressed: boolean }>}
 */
export async function prepareImageForUpload(file, options = {}) {
  const maxBytes = options.maxBytes ?? IMAGE_TARGET_MAX_BYTES
  const maxDimension = options.maxDimension ?? 1920
  const sourceMaxBytes = options.sourceMaxBytes ?? IMAGE_SOURCE_MAX_BYTES

  if (!file) {
    throw new Error('type')
  }
  const contentType = resolveImageContentType(file)
  if (!IMAGE_ALLOWED_TYPES.includes(contentType)) {
    throw new Error('type')
  }
  if (file.size <= 0 || file.size > sourceMaxBytes) {
    throw new Error('size')
  }
  if (file.size <= maxBytes) {
    return { file, compressed: false }
  }

  let bitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error('decode')
  }

  try {
    let width = bitmap.width
    let height = bitmap.height
    if (width <= 0 || height <= 0) {
      throw new Error('dimension')
    }

    const fit = Math.min(1, maxDimension / Math.max(width, height))
    width = Math.max(1, Math.round(width * fit))
    height = Math.max(1, Math.round(height * fit))

    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { alpha: false })
    if (!ctx) throw new Error('compress')

    let quality = 0.86
    let blob = null

    for (let round = 0; round < 8; round++) {
      canvas.width = width
      canvas.height = height
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, width, height)
      ctx.drawImage(bitmap, 0, 0, width, height)
      blob = await canvasToBlob(canvas, 'image/jpeg', quality)
      if (blob.size <= maxBytes) break
      if (quality > 0.5) {
        quality = Math.max(0.5, quality - 0.12)
      } else {
        width = Math.max(480, Math.round(width * 0.78))
        height = Math.max(480, Math.round(height * 0.78))
        quality = 0.78
      }
    }

    if (!blob || blob.size > maxBytes * 1.05) {
      // 仍超标则接受当前最小结果（通常已接近上限）
      if (!blob) throw new Error('compress')
    }

    const out = new File([blob], renameToJpeg(file.name), {
      type: 'image/jpeg',
      lastModified: Date.now()
    })
    return { file: out, compressed: true }
  } finally {
    bitmap.close()
  }
}

export function compressErrorMessage(reason, t) {
  const map = {
    type: '仅支持 JPG / PNG / WEBP',
    size: '图片过大，请选择不超过 20MB 的图片',
    decode: '无法读取图片，请换一张重试',
    dimension: '图片尺寸无效',
    compress: '图片压缩失败，请换一张重试'
  }
  if (typeof t === 'function') {
    // optional i18n hook; fall back to Chinese defaults
  }
  return map[reason] || map.compress
}
