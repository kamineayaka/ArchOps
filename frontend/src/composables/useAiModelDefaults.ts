import type { AiModelInfo, ModelDefaults } from '@/api/ai-providers'

/** Mutable slice of provider form fields filled from model defaults. Does not touch reasoning. */
export interface ModelDefaultsTarget {
  maxOutputTokens?: number
  contextWindow?: number
}

/**
 * Apply catalog / fetch defaults when values are &gt; 0.
 * Never mutates reasoningEffort. Returns whether any field changed.
 */
export function applyModelDefaults(
  target: ModelDefaultsTarget,
  defaults: Pick<AiModelInfo, 'maxOutputTokens' | 'contextWindow'> | ModelDefaults | null | undefined,
): boolean {
  if (!defaults) return false
  let applied = false
  const tokens = defaults.maxOutputTokens ?? 0
  const context = defaults.contextWindow ?? 0
  if (tokens > 0) {
    target.maxOutputTokens = tokens
    applied = true
  }
  if (context > 0) {
    target.contextWindow = context
    applied = true
  }
  return applied
}

export function apiKeySatisfied(opts: {
  apiKey: string | undefined | null
  editing: boolean
  hasStoredKey: boolean
}): boolean {
  if (opts.apiKey != null && String(opts.apiKey).trim().length > 0) {
    return true
  }
  return opts.editing && opts.hasStoredKey
}

export function canFetchModels(opts: {
  name: string
  baseUrl: string
  apiKey: string | undefined | null
  editing: boolean
  hasStoredKey: boolean
}): boolean {
  return Boolean(
    opts.name.trim() &&
      opts.baseUrl.trim() &&
      apiKeySatisfied({
        apiKey: opts.apiKey,
        editing: opts.editing,
        hasStoredKey: opts.hasStoredKey,
      }),
  )
}
