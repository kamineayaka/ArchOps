import { registerAssetType } from './registry'

registerAssetType({
  kind: 'SERVER',
  labelKey: 'assets.kindServer',
  defaultPort: 22,
  connectAction: 'terminal',
  showHost: true,
  showPort: true,
})

registerAssetType({
  kind: 'CLUSTER',
  labelKey: 'assets.kindCluster',
  defaultPort: 6443,
  connectAction: 'none',
  showHost: true,
  showPort: true,
})

registerAssetType({
  kind: 'SERVICE',
  labelKey: 'assets.kindService',
  defaultPort: 80,
  connectAction: 'none',
  showHost: true,
  showPort: true,
})

registerAssetType({
  kind: 'NETWORK',
  labelKey: 'assets.kindNetwork',
  defaultPort: 0,
  connectAction: 'none',
  showHost: false,
  showPort: false,
})

registerAssetType({
  kind: 'DATABASE',
  labelKey: 'assets.kindDatabase',
  defaultPort: 5432,
  connectAction: 'none',
  showHost: true,
  showPort: true,
})
