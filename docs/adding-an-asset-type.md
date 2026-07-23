# Adding an asset type

ArchOps uses a **dual registry** (backend SPI + frontend register) so new asset kinds extend via registration, not by editing shared `switch (kind)` logic.

## Principles

1. **Open for extension, closed for modification** — add a handler + a frontend registration; do **not** add type-specific branches in `AssetService`, schedulers, or shared UI helpers.
2. **Shared code must not `switch` on kind** for type-owned behavior (defaults, validation, connect action, form fields). Look up the registry instead.
3. **Stub is enough to prove OCP** — e.g. `DATABASE` ships as a minimal handler without a full DB GUI.

## Backend (`com.archops.asset.type`)

1. Add the enum value to `AssetKind` if it is a first-class kind (e.g. `DATABASE`).
2. Implement `AssetTypeHandler` as a Spring `@Component`:

   - `type()` — must equal `AssetKind.name()`
   - `defaultPort()` / `policyKind()` — e.g. `SSH` or `GENERIC`
   - `safeView(Asset)` — sanitized map/DTO (never secrets)
   - `validateCreate` / `validateUpdate` — kind-specific rules

3. Spring injects all handlers into `AssetTypeRegistry`. Use `findRequired(kind.name())` from services.

4. Optional discovery API: `GET /api/asset-types` returns `{ type, defaultPort, policyKind }`.

Example sketch:

```java
@Component
public class DatabaseAssetTypeHandler extends AbstractAssetTypeHandler {
    @Override public String type() { return AssetKind.DATABASE.name(); }
    @Override public int defaultPort() { return 5432; }
    @Override public String policyKind() { return "GENERIC"; }
}
```

`AssetService.create` / `update` already call `registry.findRequired(...).validateCreate/Update` **before** save. Do not reintroduce kind switches there.

## Frontend (`frontend/src/assetTypes`)

1. Call `registerAssetType({ kind, labelKey, defaultPort, connectAction?, showHost, showPort })`.
2. Register from `frontend/src/assetTypes/index.ts` (or a dedicated file imported by the index).
3. Add i18n keys (`assets.kindXxx`) in both `zh-CN` and `en-US`.
4. Views such as `AssetsView` build `kindOptions` from `listAssetTypes()` and apply `defaultPort` when kind changes.

```ts
registerAssetType({
  kind: 'DATABASE',
  labelKey: 'assets.kindDatabase',
  defaultPort: 5432,
  connectAction: 'none',
  showHost: true,
  showPort: true,
})
```

## Checklist for a new type

- [ ] `AssetKind` enum value (if persisted)
- [ ] `@Component` `AssetTypeHandler` (+ unit coverage that the registry discovers it)
- [ ] Frontend `registerAssetType` + i18n
- [ ] **No** new `switch (kind)` in shared services or shared UI
- [ ] Docs / plan checklist updated if this was a tracked ML task

## Out of scope for the SPI itself

Jump / proxy chains, grants, and full DB/Kafka GUIs belong to later tasks — keep the handler stub thin until those land.
