# Spec: Restore KMP navigation hierarchy, search, and list chrome

After the KMP / Decompose migration, secondary screens lost stack semantics, Config and Apps search lost the Material3 SearchBar flow, and list screens either double-apply a top bar or never tint it on scroll.

This spec restores those three surfaces. Bottom tabs stay Config + Home. Apps, Logcat, Settings, subscriptions, QR, route settings, and node edit stay secondary — they become a real Decompose stack instead of a hand-rolled overlay.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Root tabs | Config + Home only. Do not put Apps / Logcat / Settings back on the floating nav. |
| Secondary navigation | Decompose `ChildStack` over the pager, not a patched `ArrayList<RootOverlay>`. |
| Push / pop motion | Material stack: enter from the right, pop exits to the right. Android predictive back. |
| Config search | Circular 56dp `SearchBar` + `ExpandedFullScreenSearchBar` (pre-KMP Config). |
| Apps search | `SearchBar` in the top-bar title slot + the same full-screen expansion (pre-KMP Apps). |
| List chrome | `LargeTopAppBar` + `exitUntilCollapsed` on list/settings screens. Home stays compact. |
| Shared elements | Do not restore Navigation3 shared-element transitions. |
| Logcat search | Out of scope. |

## Architecture

### Tabs (unchanged model)

`DefaultRootComponent` keeps `PagesNavigation<RootTab>` and `childPages` with `RootTab.Config` and `RootTab.Home`. Default selected tab stays Home. `XrayFloatingNav` still has those two items.

### Overlay stack (replace)

Delete `RootOverlay` as the live navigation type and delete `overlayStack: ArrayList<RootOverlay>` / `MutableValue<RootOverlay>` in `DefaultRootComponent`.

Add `StackNavigation<RootStackConfig>` and `childStack(...)`.

```kotlin
@Serializable
sealed interface RootStackConfig {
    @Serializable data object Idle : RootStackConfig
    @Serializable data object Settings : RootStackConfig
    @Serializable data object Subscriptions : RootStackConfig
    @Serializable data object QrScanner : RootStackConfig
    @Serializable data object Apps : RootStackConfig
    @Serializable data object Logcat : RootStackConfig
    @Serializable data object RouteSettings : RootStackConfig
    @Serializable data class NodeEdit(val nodeId: Long) : RootStackConfig // 0 = create
}
```

`ChildStack` cannot be empty. `Idle` is the sentinel: it renders nothing. Initial stack is `[Idle]`. `pop` must not remove `Idle`. Closing every secondary screen is `popWhile { it !is Idle }` or `replaceAll(listOf(Idle))`.

`RootComponent` public API stays verb-based (`openSettings()`, `openApps()`, `navigateBack()`, …). Callers do not push configs themselves.

### Push rules

| Call | Also | Stack effect |
|------|------|----------------|
| `openSettings()` | — | Bring `Settings` to top, or `push` if absent. |
| `openSubscriptions()` | `selectTab(Config)` | Bring or push `Subscriptions`. |
| `openQrScanner()` | `selectTab(Config)` when opened from Config or a shortcut. Do not change tab if `Subscriptions` is already active. | Always `push` `QrScanner` (allowed on top of `Subscriptions`). |
| `openApps()` / `openLogcat()` / `openRouteSettings()` | — | Push on top of `Settings` when Settings is already active; otherwise push on `Idle`. |
| `onOpenCreateNode()` / `onOpenEditNode(id)` | — | Push `NodeEdit(0)` or `NodeEdit(id)`. |
| `navigateBack()` | — | `pop()` if active is not `Idle`; no-op on `Idle`. |

Same destination type does not stack twice: if that config is already in the stack, bring it to top (drop the older instance, then push). `NodeEdit` is keyed by `nodeId`, so create and edit of different ids can follow each other; a second edit of the same id replaces the existing one.

### Child ownership

| Stack child | Component |
|-------------|-----------|
| `Settings` | Existing always-alive `settingsComponent`. |
| `Subscriptions` | Create with the stack child context (today `rememberSubscriptionComponent()` in the overlay host). |
| `NodeEdit` | Reuse `SharedEditScreen` + `NodeFormEditor`. Save success and back both `pop`. Remove `ConfigState.nodeEditTarget` and the `ConfigTabScreen` early-return. |
| Apps / Logcat / QR | Existing `PlatformRootHooks`. |

`AgentScreen.toRootNavigation()` and shortcuts (`AndroidRootActionCoordinator`) select the tab first, then call the same `open*` methods. Delete the live use of `RootOverlay` and the Navigation3 `toDestination()` path once tests are updated to `RootStackConfig`.

### Rendering

`RootContent`:

1. `ChildPages` full size (tabs).
2. `Children(stack)` full size on top. `Idle` = empty. Other children fill the box and consume pointer input so the pager cannot be swiped while a stack page is active.
3. Floating nav visible only when `stack.active == Idle` and Config chrome is not covered and the existing scroll-hide flag is true.

`showBottomNav` no longer reads `RootOverlay`. Node edit covering chrome is implied by `NodeEdit` on the stack.

## Motion

- Tab pager: keep `PagesScrollAnimation.Default`.
- Stack: Decompose `Children` + `stackAnimation(slide())`. Push: new page from the right, previous page eases left. Pop: reverse. `Idle` is empty, so Home/Config → Settings looks like Settings covering a still pager; pop to `Idle` slides the page off to the right and reveals the tabs.
- Android: wrap with `predictiveBackAnimation` using the root `BackHandler` and `navigateBack`. iOS: same slide, no predictive back.
- Bottom nav show/hide: keep the current vertical slide + fade.
- Do not add scale/shared-element route transitions.

## Search

### Shared behavior

- Material3 `SearchBar` + `ExpandedFullScreenSearchBar` (Compose Material3 1.5.x).
- `snapshotFlow` on the input → `debounce(300)` → `distinctUntilChanged` → commit query to the component / ViewModel.
- IME `Search`: clear focus, hide keyboard, collapse after ~400ms (pre-KMP timing).
- Clear control empties the query and restores the unfiltered list.
- Expanded search hides the floating bottom nav (Config).

If commonMain Material3 is missing `ExpandedFullScreenSearchBar`, implement the full widget in `androidMain` / Android screens and use the same API on iOS Config when present; otherwise iOS Config uses `DockedSearchBar` with the same debounce / IME / empty-state rules. Apps search on iOS stays behind `SharedInDevelopmentScreen`.

### Config

- 56dp circular collapsed `SearchBar`, bottom-end (pre-KMP bias ~0.8 x, 0.9 y), above the floating nav clearance.
- Expanded overlay lists matches on remark and URL, case-insensitive.
- Tap a result: `clearText()`, collapse, `animateScrollToItem` for that node. Do not change the selected proxy unless the user later taps the row in the main list.
- IME search without picking a row: collapse and keep the query applied to the main `nodes` list.
- Zero matches: a dedicated “no results” label. Do not show `SharedConfigEmptyContent` (add-config) for a failed search.
- `DefaultConfigComponent` exposes both the main filtered list and an overlay result list driven by the same query. Remove the `searchExpanded` + `OutlinedTextField` under the Config top bar.

### Apps

- Collapsed `SearchBar` sits in the top-bar actions / collapsed title row, not as the large expanded title (the large title stays “Apps”). Expanding still uses `ExpandedFullScreenSearchBar`.
- Filter `appName` and `packageName`.
- Empty copy: `appsNoPackagesMessage` only when the unfiltered list is empty; a distinct “no matches” string when the query filters everything out.
- Single source of truth: `AppsViewmodel.searchQuery`. Delete the `remember { mutableStateOf("") }` in `AppsScreen`.
- `AndroidAppsScreen` does not host its own `Scaffold` / `TopAppBar`. It calls the shared picker with `onBack`.

## List chrome

### Double scaffold

- `AndroidAppsScreen`: no outer `Scaffold`. Pass `onBack` into `SharedAppsPickerScreen`.
- `AndroidLogcatScreen`: no outer `Scaffold`. Pass back into `SharedAppLogScreen` as the navigation icon (the warning action stays in `actions`).
- One top inset per screen.

### `SharedListScaffold`

Thin shared wrapper used by Settings, Config, Apps, Logcat, Subscriptions, Route settings:

- `LargeTopAppBar` + `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`.
- Content `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`.
- Colors: expanded container transparent / background; collapsed `scrolledContainerColor = surface` plus the default collapsed elevation.
- Optional `navigationIcon`, `actions`, `title`, `footerUnderBar` (Config filter chips sit under the collapsing bar, not inside the large title). Apps puts its collapsed `SearchBar` in `actions` (or the collapsed title row), not in the large title.

Home keeps `HomeTopBar` (compact). QR has no app bar. `SharedEditScreen` keeps a compact `TopAppBar` but must attach the declared `pinnedScrollBehavior` to the content `nestedScroll`, replacing the scroll-position-only shadow as the source of truth.

If the large title still feels too tall after a device check, switch `SharedListScaffold` to a compact `TopAppBar` and keep the same `scrollBehavior` / surface-on-scroll. Do not reintroduce a second scaffold to “fix” spacing.

## Testing

- Unit-test stack verbs: `openSettings` then `openApps` then `navigateBack` returns to Settings; a second `openSettings` does not push a second Settings; `navigateBack` on `Idle` is a no-op; `NodeEdit(0)` pops on close.
- Update `AgentScreenRootTabTest` (and any overlay tests) from `RootOverlay` / `toDestination()` to `toRootNavigation()` + stack config.
- Do not add screenshot tests in this pass.

## Out of scope

- Four-tab or Settings-as-tab bottom nav.
- Navigation3 / `NavDisplay` / shared-element restore.
- Logcat line search.
- iOS Apps implementation.
- Tablet Config list–detail.
- Rewriting tab pager into the same `ChildStack`.

## Success criteria

1. Settings → Apps → back lands on Settings with opposite-direction slide (and Android predictive back).
2. Config and Apps search match the pre-KMP SearchBar / full-screen results feel, including debounce and a real empty-results state.
3. Apps and Logcat show one title, with no double top inset.
4. Listed list screens collapse the large title on scroll and the collapsed bar is visually distinct from the list.
5. Bottom nav still has only Config and Home, hidden whenever the stack is not `Idle`.
