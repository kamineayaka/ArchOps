import type { Router } from 'vue-router'
import type { MessageApiInjection } from 'naive-ui/es/message/src/MessageProvider'
import type { Asset } from '@/api/assets'
import '@/assetTypes'
import { connectActionFor, getAssetType } from '@/assetTypes/registry'

type Translate = (key: string, params?: Record<string, unknown>) => string

/**
 * Registry-driven connect dispatch. Views must call this instead of branching on kind.
 */
export function openAsset(
  asset: Pick<Asset, 'id' | 'kind' | 'hasSshCredential' | 'name'>,
  deps: {
    router: Router
    message: MessageApiInjection
    t: Translate
  },
): void {
  const action = connectActionFor(asset.kind)
  const def = getAssetType(asset.kind)

  switch (action) {
    case 'terminal':
      if (!asset.hasSshCredential) {
        deps.message.warning(deps.t('assets.connectNeedsCredential'))
        void deps.router.push({ name: 'assets' })
        return
      }
      void deps.router.push({ name: 'terminal', params: { assetId: String(asset.id) } })
      return
    case 'query':
      deps.message.info(
        deps.t('assets.connectQueryDeferred', {
          name: asset.name,
          kind: def ? deps.t(def.labelKey) : asset.kind,
        }),
      )
      void deps.router.push({ name: 'assets', query: { highlight: String(asset.id) } })
      return
    case 'page':
      deps.message.info(
        deps.t('assets.connectPageDeferred', {
          name: asset.name,
          kind: def ? deps.t(def.labelKey) : asset.kind,
        }),
      )
      void deps.router.push({ name: 'assets', query: { highlight: String(asset.id) } })
      return
    case 'none':
    default:
      deps.message.info(deps.t('assets.connectNone', { name: asset.name }))
      void deps.router.push({ name: 'assets' })
  }
}
