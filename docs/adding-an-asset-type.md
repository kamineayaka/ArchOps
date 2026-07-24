# Adding an asset type

ArchOps uses a **dual registry** (backend SPI + frontend register) so new asset kinds extend via registration, not by editing shared `switch (kind)` logic.

Product metaphor: **SSH / SERVER ≈ IDE terminal**; other types use **`query` / `page`**; Agent may do read-only probes. Panels can ship later — **form + test-connection must work first**.

## Principles

1. **Open for extension, closed for modification** — add a handler + a frontend registration; do **not** add type-specific branches in `AssetService`, schedulers, or shared UI helpers.
2. **Shared code must not `switch` on kind** for type-owned behavior (defaults, validation, connect action, form fields, probes). Look up the registry / `openAsset` instead.
3. **Connect dispatch is centralized** — `frontend/src/assetTypes/openAsset.ts` and handler `connectAction()`; unimplemented panels show a friendly tip (never throw).
4. **Secrets stay encrypted** — reuse credential cipher storage; never log plaintext passwords/keys; Description / notes ≠ Architecture SSOT.

## Backend (`com.archops.asset.type`)

1. Add the enum value to `AssetKind` if it is a first-class kind (e.g. `DATABASE`, later `K8S`).
2. Implement `AssetTypeHandler` as a Spring `@Component`:

   | Method | Purpose |
   |--------|---------|
   | `type()` | Must equal `AssetKind.name()` |
   | `defaultPort()` / `policyKind()` | Defaults + risk family (`SSH` / `GENERIC`) |
   | `connectAction()` | `TERMINAL` \| `QUERY` \| `PAGE` \| `NONE` |
   | `safeView(Asset)` | Sanitized map (never secrets) |
   | `validateCreate` / `validateUpdate` | Kind-specific rules |
   | `testConnection(ConnectivityContext)` | Type-owned probe (SSH / TCP / JDBC / …) |

3. Spring injects all handlers into `AssetTypeRegistry`. Services call `findRequired(kind.name())` — **no shared switch**.
4. Discovery API: `GET /api/asset-types` → `{ type, defaultPort, policyKind, connectAction }`.
5. `POST /api/assets/test-connection` resolves the handler by `kind` (or saved asset kind) and delegates to `testConnection`.

Example (DATABASE already ships):

```java
@Component
public class DatabaseAssetTypeHandler extends AbstractAssetTypeHandler {
    @Override public String type() { return AssetKind.DATABASE.name(); }
    @Override public int defaultPort() { return 5432; }
    @Override public ConnectAction connectAction() { return ConnectAction.QUERY; }
    @Override public TestConnectionResponse testConnection(ConnectivityContext ctx) { /* TCP + optional JDBC */ }
}
```

Typed non-secret config (e.g. database name) goes in **asset metadata JSON**. Passwords use the existing encrypted credential row (`username` + `PASSWORD` secret); jump asset IDs may be stored for later tunnel use.

## Frontend (`frontend/src/assetTypes`)

1. Call `registerAssetType({ ... })` from `index.ts` (or a dedicated module imported by the index).
2. Add i18n keys (`assets.kindXxx`) in both `zh-CN` and `en-US`.
3. Create form reads **registry flags** (`authMode`, `showDatabaseName`, `supportsTest`) — do not hardcode `kind === 'SERVER'` in views.
4. List / tree **Connect** calls `openAsset(asset, { router, message, t })`.

```ts
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
```

### connectAction matrix

| Action | Meaning | Unimplemented panel |
|--------|---------|---------------------|
| `terminal` | WebSSH IDE | Require credential; open `/terminal/:assetId` |
| `query` | Generic query shell | Friendly toast; stay on assets (panel may be W4c) |
| `page` | Dedicated management page | Friendly toast |
| `none` | No primary connect | Info toast + assets list |

Optional later: register a Vue `ConfigSection` component per type; until then, registry flags drive the shared create form.

## Checklist for a new type

- [ ] `AssetKind` enum value (if persisted)
- [ ] `@Component` `AssetTypeHandler` with `connectAction` + `testConnection` (+ registry unit coverage)
- [ ] Frontend `registerAssetType` + i18n (`authMode` / `supportsTest` as needed)
- [ ] Create form can save + test without editing `AssetService` switches
- [ ] List/tree Connect goes through `openAsset` (friendly tip if panel deferred)
- [ ] **No** new `switch (kind)` in shared services or shared UI
- [ ] Docs / W4 acceptance updated if tracked

## Out of scope for a thin type stub

Full K8s/Kafka consoles, SFTP, RDP, Description-as-Architecture, write tools without approval. Agent read-only probes (`db_ping`, …) land in W4d.
