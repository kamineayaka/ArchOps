export type AssetConnectAction = 'terminal' | 'none'

export interface AssetTypeDefinition {
  kind: string
  labelKey: string
  defaultPort: number
  connectAction?: AssetConnectAction
  showHost: boolean
  showPort: boolean
}

const registry = new Map<string, AssetTypeDefinition>()

export function registerAssetType(def: AssetTypeDefinition): void {
  if (registry.has(def.kind)) {
    throw new Error(`Asset type already registered: ${def.kind}`)
  }
  registry.set(def.kind, {
    connectAction: 'none',
    ...def,
  })
}

export function getAssetType(kind: string): AssetTypeDefinition | undefined {
  return registry.get(kind)
}

export function requireAssetType(kind: string): AssetTypeDefinition {
  const def = registry.get(kind)
  if (!def) {
    throw new Error(`Unknown asset type: ${kind}`)
  }
  return def
}

export function listAssetTypes(): AssetTypeDefinition[] {
  return Array.from(registry.values())
}

export function defaultPortFor(kind: string): number | null {
  const port = registry.get(kind)?.defaultPort
  if (port == null || port <= 0) return null
  return port
}
