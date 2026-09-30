# Spec: Liquid glass floating navigation

The shared floating nav (`XrayFloatingNav`) stays the home for Config and Home. Its opaque Material surface becomes liquid glass. Android matches the Backdrop catalog `LiquidBottomTabs` demo. iOS 26 uses the system liquid glass effect. Earlier iOS versions use `UIBlurEffect` for the same structure.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Scope | Floating pill, selection slider, and the trailing search circle. Nothing else (no top bars, no bottom fade restyle). |
| Android effect | Full `LiquidBottomTabs` behavior: bar vibrancy, blur, and lens; slider is a draggable lens with press scale, damped drag, chromatic aberration, and snap to the nearest tab. |
| Android dependency | `io.github.kyant0:backdrop` on the `shared` Android source set only. Raise Kotlin and Compose Multiplatform to the versions that artifact requires. |
| Slider gesture | Replaces the current translucent color chip and its spring slide. Icon scale and selected label color stay. |
| Search circle | Same glass surface as the bar. It is not part of the drag gesture. |
| iOS 26 | Native liquid glass (`UIGlassEffect` / equivalent) for the bar, slider, and search circle. |
| iOS below 26 | `UIBlurEffect` for the bar, slider, and search circle. Same drag-and-snap structure. |
| Content sampling | Android samples the tab pages (`ChildPages`) via `layerBackdrop`. `FloatingNavBottomFade` stays outside that layer. |
| Fallback | If the backdrop layer is not ready, or the effect cannot run, paint the current solid surface color. Tabs remain tappable. |

## Architecture

`XrayFloatingNav` in `commonMain` keeps item layout, labels, icon scale, selected color, trailing slot, and tab selection callback.

The opaque `Surface` on the pill and on the search circle is replaced by a platform chrome. The selection chip is no longer a shared `background` box. Android draws the catalog liquid slider. iOS draws a native glass or blur slider that follows the same drag-and-snap index.

`RootContent` marks the tab pages as the backdrop layer and passes that backdrop into the nav on Android. The fade overlay is a sibling drawn outside the layer, so the glass shows page content.

Platform split:

- Android actual uses `drawBackdrop` with the catalog effect stack (`vibrancy`, `blur`, `lens`, highlight, shadow, inner shadow) and the catalog drag animation (`DampedDragAnimation`, press scale, chromatic aberration).
- iOS actual hosts a `UIVisualEffectView`: `UIGlassEffect` on iOS 26, `UIBlurEffect` otherwise.

Shared code does not reference Backdrop types. iOS does not depend on the Backdrop artifact.

## Components

| Unit | Role |
|------|------|
| `XrayFloatingNav` | Shared layout, two tabs, trailing search, selection callback. |
| Android liquid chrome | Bar, lens slider, and search circle. Owns drag, press, and snap. Calls `onItemSelected` when the index settles. |
| iOS glass chrome | Bar, slider, and search circle using system glass or blur. Drag snaps to a tab and calls `onItemSelected`. |
| Backdrop source | `ChildPages` in `RootContent`, wrapped with `layerBackdrop`. |

The search control's shared-element container stays transparent so it does not paint a second disc on top of the glass circle.

## Data flow

1. `RootContent` creates the layer backdrop around the tab pages.
2. The nav reads the selected tab index from `RootTab`.
3. A drag updates the slider locally. On release, the index snaps and `component.selectTab` runs.
4. A tap on a tab selects that index the same way.
5. The search circle click still calls `component::openSearch`.

## Error handling

- Backdrop not ready: solid `surfaceContainerHighest` on the bar, slider, and search circle.
- Effect unsupported on the device: same solid fallback. Clicks and tab changes still work.
- iOS version check picks glass or blur. There is no third visual state.

## Testing

- Android device: drag the slider and compare press scale, damping, chromatic aberration, and snap with the Backdrop `LiquidBottomTabs` demo.
- Android: tapping Config or Home changes the tab. Search still opens.
- Android light and dark: the pill shows the page underneath, not the bottom fade.
- iOS 26: bar, slider, and search use system liquid glass and still switch tabs.
- iOS below 26: the same controls use system blur and still switch tabs.
- Force the fallback path (backdrop absent): solid chrome, tabs still switch.
