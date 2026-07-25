/** 浏览器侧本地 SSH 私钥发现（需用户授权目录/文件；无法静默读 ~/.ssh）。 */

export interface LocalSshKeyEntry {
  id: string
  name: string
  pathLabel: string
  content: string
  fingerprint: string
}

function isPrivateKeyName(name: string): boolean {
  const n = name.toLowerCase()
  if (n.endsWith('.pub')) return false
  if (n.endsWith('.pem') || n.endsWith('.key')) return true
  return /^(id_|identity|ssh_host_)/.test(n) || n === 'id_rsa' || n === 'id_ed25519' || n === 'id_ecdsa' || n === 'id_dsa'
}

async function sha256Short(text: string): Promise<string> {
  try {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
    const hex = [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('')
    return `SHA256:${hex.slice(0, 16)}…`
  } catch {
    return 'SHA256:—'
  }
}

async function entryFromFile(file: File, pathLabel?: string): Promise<LocalSshKeyEntry | null> {
  if (!isPrivateKeyName(file.name) && !file.name.includes('id_')) {
    // still allow explicit browse of any file
  }
  const content = await file.text()
  if (!content.trim()) return null
  const looksLikeKey =
    content.includes('PRIVATE KEY') || content.includes('openssh-key-v1') || content.trim().startsWith('-----')
  if (!looksLikeKey && !isPrivateKeyName(file.name)) return null
  const fingerprint = await sha256Short(content)
  const label = pathLabel || file.name
  return {
    id: `${label}:${fingerprint}`,
    name: file.name,
    pathLabel: label,
    content,
    fingerprint,
  }
}

export function supportsDirectoryPicker(): boolean {
  return typeof window !== 'undefined' && 'showDirectoryPicker' in window
}

/** 让用户选择 ~/.ssh 目录并扫描私钥。 */
export async function scanSshDirectory(): Promise<LocalSshKeyEntry[]> {
  if (!supportsDirectoryPicker()) {
    throw new Error('DIRECTORY_PICKER_UNSUPPORTED')
  }
  type DirHandle = {
    name: string
    entries: () => AsyncIterableIterator<[string, { kind: string; getFile: () => Promise<File> }]>
  }
  const picker = (
    window as unknown as {
      showDirectoryPicker: (opts: { mode: string }) => Promise<DirHandle>
    }
  ).showDirectoryPicker
  const dir = await picker({ mode: 'read' })
  const out: LocalSshKeyEntry[] = []
  for await (const [name, handle] of dir.entries()) {
    if (handle.kind !== 'file') continue
    if (!isPrivateKeyName(name)) continue
    const file = await handle.getFile()
    const entry = await entryFromFile(file, `${dir.name}/${name}`)
    if (entry) out.push(entry)
  }
  return out
}

/** `<input type="file">` 选择一个或多个私钥文件。 */
export async function readKeyFiles(fileList: FileList | File[]): Promise<LocalSshKeyEntry[]> {
  const files = Array.from(fileList)
  const out: LocalSshKeyEntry[] = []
  for (const file of files) {
    const entry = await entryFromFile(file, file.name)
    if (entry) out.push(entry)
  }
  return out
}
