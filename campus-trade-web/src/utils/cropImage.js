/**
 * 从 Cropper 实例导出裁剪后的 File（JPEG）。
 * @param {import('cropperjs').default} cropper
 * @param {{ fileName?: string, maxWidth?: number, maxHeight?: number, quality?: number }} options
 * @returns {Promise<File>}
 */
export function getCroppedFile(cropper, options = {}) {
  if (!cropper) {
    return Promise.reject(new Error('cropper'))
  }
  const maxWidth = options.maxWidth ?? 1024
  const maxHeight = options.maxHeight ?? 1024
  const quality = options.quality ?? 0.92
  const fileName = options.fileName || 'cropped.jpg'

  const canvas = cropper.getCroppedCanvas({
    maxWidth,
    maxHeight,
    imageSmoothingEnabled: true,
    imageSmoothingQuality: 'high'
  })
  if (!canvas) {
    return Promise.reject(new Error('crop'))
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('crop'))
          return
        }
        resolve(
          new File([blob], fileName.replace(/\.[^.]+$/, '') + '.jpg', {
            type: 'image/jpeg',
            lastModified: Date.now()
          })
        )
      },
      'image/jpeg',
      quality
    )
  })
}
