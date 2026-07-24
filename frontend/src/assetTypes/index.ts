import { registerAssetType } from './registry'

registerAssetType({
  kind: 'SERVER',
  labelKey: 'assets.kindServer',
  defaultPort: 22,
  connectAction: 'terminal',
  authMode: 'ssh',
  showHost: true,
  showPort: true,
  supportsTest: true,
})

registerAssetType({
  kind: 'CLUSTER',
  labelKey: 'assets.kindCluster',
  defaultPort: 6443,
  connectAction: 'none',
  authMode: 'none',
  showHost: true,
  showPort: true,
})

registerAssetType({
  kind: 'SERVICE',
  labelKey: 'assets.kindService',
  defaultPort: 80,
  connectAction: 'none',
  authMode: 'none',
  showHost: true,
  showPort: true,
})

registerAssetType({
  kind: 'NETWORK',
  labelKey: 'assets.kindNetwork',
  defaultPort: 0,
  connectAction: 'none',
  authMode: 'none',
  showHost: false,
  showPort: false,
})

registerAssetType({
  kind: 'DATABASE',
  labelKey: 'assets.kindDatabase',
  defaultPort: 5432,
  connectAction: 'query',
  authMode: 'password',
  showHost: true,
  showPort: true,
  showDatabaseName: true,
  supportsTest: true,
})

registerAssetType({
  kind: 'K8S',
  labelKey: 'assets.kindK8s',
  defaultPort: 6443,
  connectAction: 'page',
  authMode: 'token',
  showHost: true,
  showPort: true,
  showK8sMode: true,
  supportsTest: true,
})

registerAssetType({
  kind: 'REDIS',
  labelKey: 'assets.kindRedis',
  defaultPort: 6379,
  connectAction: 'query',
  authMode: 'password',
  showHost: true,
  showPort: true,
  supportsTest: true,
})
