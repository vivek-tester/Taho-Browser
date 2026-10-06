package app.taho.browser.shell

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.taho.browser.shell.R

/**
 * Spec §5 — the authored icon family, and the only way a drawable reaches the
 * screen.
 *
 * Every icon is a 24dp vector stroked at 1.5dp with round caps and joins, drawn
 * on the same 24-unit grid, so the set reads as one family at one weight. The
 * paths are painted in white and the caller's [TahoIcon] `tint` is applied as a
 * `currentColor` colour filter, which is why no colour is baked into the
 * drawables and why the tint must come from a `TahoTheme.kt` token.
 *
 * `ic_taho_brand` is the one exception to the stroke rule: the crest is a
 * filled mark. It still sits on the same grid and at the same optical size.
 *
 * `ic_taho_overflow` draws its three dots the way `ic_taho_storage` draws the one
 * inside its second rule — a 0.6-long dash, which a round cap turns into a dot of
 * the family's stroke weight. A zero-length subpath would be the more obvious
 * spelling, but relies on renderer handling of degenerate strokes.
 *
 * This composable is deliberately the single funnel. Emoji used as icons render
 * inconsistently per device and announce inconsistently to screen readers
 * (spec §3); routing every mark through [TahoIcon] is what makes that
 * enforceable rather than a convention. [description] is what replaces the
 * `contentDescription` semantics calls that used to sit on the glyphs: any icon
 * that is the sole content of an interactive control must pass one, and passing
 * a null there leaves that control unlabelled.
 *
 * Inline typographic marks in prose are **not** icons and must not come through
 * here — `"✓ complete"` and `"📁 Requests"` stay `Text`. The same is true of the
 * `iconGlyph` / `avatarGlyph` data fields on the models: those are
 * caller-supplied content, not chrome.
 */
@Composable
internal fun TahoIcon(
    name: TahoIconName,
    tint: Color,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(name.drawable),
        contentDescription = description,
        tint = tint,
        modifier = modifier,
    )
}

/**
 * The manifest, as a closed type. A bare `String` would let `TahoIcon("serach",
 * …)` compile; this makes a typo a build error and lets the compiler check that
 * every call site names a member of the family.
 *
 * Each constant maps to exactly one `res/drawable/ic_taho_*.xml`. The file-name
 * convention (`chevron-right` → `ic_taho_chevron_right.xml`) is asserted by
 * `TahoIconSetTest`, so the manifest cannot drift from the directory.
 */
internal enum class TahoIconName(@DrawableRes val drawable: Int) {
    Alert(R.drawable.ic_taho_alert),
    Autoplay(R.drawable.ic_taho_autoplay),
    Bolt(R.drawable.ic_taho_bolt),
    Book(R.drawable.ic_taho_book),
    Brand(R.drawable.ic_taho_brand),
    Camera(R.drawable.ic_taho_camera),
    Card(R.drawable.ic_taho_card),
    CaretDown(R.drawable.ic_taho_caret_down),
    CaretUp(R.drawable.ic_taho_caret_up),
    Check(R.drawable.ic_taho_check),
    ChevronLeft(R.drawable.ic_taho_chevron_left),
    ChevronRight(R.drawable.ic_taho_chevron_right),
    Clipboard(R.drawable.ic_taho_clipboard),
    Clock(R.drawable.ic_taho_clock),
    Close(R.drawable.ic_taho_close),
    Expand(R.drawable.ic_taho_expand),
    ExternalLink(R.drawable.ic_taho_external_link),
    Grid(R.drawable.ic_taho_grid),
    Home(R.drawable.ic_taho_home),
    Incognito(R.drawable.ic_taho_incognito),
    Location(R.drawable.ic_taho_location),
    Lock(R.drawable.ic_taho_lock),
    Microphone(R.drawable.ic_taho_microphone),
    Minus(R.drawable.ic_taho_minus),
    Monitor(R.drawable.ic_taho_monitor),
    MonitorCheck(R.drawable.ic_taho_monitor_check),
    Notification(R.drawable.ic_taho_notification),
    Overflow(R.drawable.ic_taho_overflow),
    Person(R.drawable.ic_taho_person),
    Plus(R.drawable.ic_taho_plus),
    Popups(R.drawable.ic_taho_popups),
    Reader(R.drawable.ic_taho_reader),
    Reload(R.drawable.ic_taho_reload),
    Search(R.drawable.ic_taho_search),
    Settings(R.drawable.ic_taho_settings),
    Shield(R.drawable.ic_taho_shield),
    ShieldCheck(R.drawable.ic_taho_shield_check),
    Sparkle(R.drawable.ic_taho_sparkle),
    Star(R.drawable.ic_taho_star),
    StarOutline(R.drawable.ic_taho_star_outline),
    Storage(R.drawable.ic_taho_storage),
    WarnTriangle(R.drawable.ic_taho_warn_triangle),
}
