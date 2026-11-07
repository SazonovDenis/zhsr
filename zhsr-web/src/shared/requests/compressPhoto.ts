/** Длинная сторона сжатого фото, пиксели. */
const MAX_SIDE = 1600
/** Качество JPEG, от 0 до 1. */
const QUALITY = 0.8

/**
 * Сжать фото перед отправкой: уменьшить до MAX_SIDE по длинной стороне и сохранить в JPEG. При плохой связи запрос с
 * несжатыми фото может раз за разом обрываться, а попытка начинается с нуля. Формат, который браузер не может
 * прочитать, остаётся как есть: фото важнее размера, сервер примет и такой файл. Сжатый файл больше исходного —
 * остаётся исходный.
 */
export async function compressPhoto(file: Blob): Promise<Blob> {
  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    return file
  }
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const compressed = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', QUALITY))
  return compressed && compressed.size < file.size ? compressed : file
}
