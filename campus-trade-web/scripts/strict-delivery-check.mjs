import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const apiDir = path.join(root, 'src', 'api')
const forbiddenMethodPattern = /method\s*:\s*['"`](put|delete|patch)['"`]/i
const mockPattern = /\b(mock|fake|dummy)\b/i

async function collectFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...await collectFiles(fullPath))
    } else if (entry.isFile() && /\.(js|vue)$/.test(entry.name)) {
      files.push(fullPath)
    }
  }
  return files
}

const failures = []
for (const file of await collectFiles(apiDir)) {
  const source = await readFile(file, 'utf8')
  const methodMatch = source.match(forbiddenMethodPattern)
  if (methodMatch) {
    failures.push(`${path.relative(root, file)} uses forbidden HTTP method: ${methodMatch[1].toUpperCase()}`)
  }
  const mockMatch = source.match(mockPattern)
  if (mockMatch) {
    failures.push(`${path.relative(root, file)} contains mock-like API wording: ${mockMatch[1]}`)
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('strict delivery check passed')
