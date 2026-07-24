export type AssetConnectAction = 'terminal' | 'query' | 'page' | 'none'

/** How credentials are collected for this type (registry-driven, not switch(kind) in views). */
export type AssetAuthMode = 'ssh' | 'password' | 'none'

export interface AssetTypeDefinition {
  kind: string
  labelKey: string
  defaultPort: number
  connectAction: AssetConnectAction
  authMode: AssetAuthMode
  showHost: boolean
  showPort: boolean
  /** Collect optional logical database / schema name. */
  showDatabaseName?: boolean
  /** Whether the create form / list can run type-owned test-connection. */
  supportsTest?: boolean
}

const registry = new Map<string, AssetTypeDefinition>()

export function registerAssetType(def: AssetTypeDefinition): void {
  if (registry.has(def.kind)) {
    throw new Error(`Asset type already registered: ${def.kind}`)
  }
  registry.set(def.kind, {
    supportsTest: false,
    showDatabaseName: false,
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

export function connectActionFor(kind: string): AssetConnectAction {
  return registry.get(kind)?.connectAction ?? 'none'
}
