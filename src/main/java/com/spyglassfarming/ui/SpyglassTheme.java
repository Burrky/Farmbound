package com.spyglassfarming.ui;

import java.awt.Color;

/**
 * The single shared source of truth for the plugin's dark, warm-bronze
 * palette, used across all three tabs (Session / Loot / History), the
 * shared tab row, cards, headers, dividers, hover states, secondary
 * text, and highlighted totals.
 *
 * TEXT ROLES (pass 30): the text and accent colours are roles, not fixed values: {@link #apply} sets them for the
 * player's contrast (Standard, already readable on every surface: supporting text ≥ 6:1, quiet labels ≥ 5:1, muted
 * metadata ≥ 4.5:1 on a tile face; or High contrast). Surfaces stay as they are. Code reads the roles when it paints or
 * builds, so a change reaches new and rebuilt components; the panel re-colours existing ones.
 *
 * SCOPE: colors only, nothing else. This class has no reference to,
 * and is never referenced by, any session/history/loot data or
 * persistence type in this codebase -- it is safe to read, reuse, or
 * revert in isolation from everything else.
 *
 * WHY SHARED (not duplicated per-file): {@link OsrsTelemetryPanel}'s tab
 * row, {@link CurrentSessionView}, {@link LootTrackerView}, and
 * {@link HistoryView}/{@link HistoryDetailView} all need to visually
 * read as parts of the SAME plugin -- defining the palette once and
 * referencing it from all of them is what keeps that cohesive rather
 * than several independently-eyeballed dark palettes drifting apart
 * over time.
 *
 * SEMANTIC vs. LOCAL COLOR: this palette is the NEUTRAL chrome layer
 * (backgrounds, card faces, borders, ordinary text) that sits around
 * -- and deliberately never replaces -- colors that carry their own
 * meaning: {@code CurrentSessionView}'s per-skill tint table
 * ({@code SKILL_TINTS}), the suspended/active session-state accents,
 * the progress-bar red/yellow/green interpolation, and Loot's own gold
 * value color (mapped onto {@link #ACCENT_GOLD} here, since that is
 * exactly what it already was). Never override these semantic colors
 * with a chrome token from this class.
 */
final class SpyglassTheme
{
	private SpyglassTheme()
	{
	}

	// -- Backgrounds / surfaces --------------------------------------

	// -- Raised Tiles (Home family): a recessed canvas with warmer, lighter tiles floating on it.
	// All opaque and pre-blended against TILE_CANVAS: no alpha per paint.

	/** A tile's face at rest: warm, a clear step lighter than the canvas. */
	static final Color TILE_FACE = new Color(0x46, 0x3A, 0x2C);
	/** The fine dark rim that gives a tile its silhouette against the canvas. */
	static final Color TILE_RIM = new Color(0x12, 0x0F, 0x0C);
	/** Quiet supporting text on a tile face (lighter than {@link #TEXT_MUTED}, which is too dim on a face). */
	static final Color TILE_TEXT_QUIET = new Color(0xBC, 0xAF, 0x92);



	// -- Borders / dividers -------------------------------------------

	/** Soft border/divider -- inactive-tab borders, in-card separator lines. */
	static final Color BORDER_SOFT = new Color(0x54, 0x42, 0x33);

	// -- Text -----------------------------------------------------------

	/** Primary text -- activity names, major metrics, key headings. */
	static final Color TEXT_PRIMARY = new Color(0xE6, 0xD8, 0xB8);
	/** Secondary text -- timestamps, rates, labels. */
	static final Color TEXT_SECONDARY = new Color(0xCA, 0xBE, 0xA2);
	/** Muted text -- unavailable states, subtle metadata, helper text. */
	static final Color TEXT_MUTED = new Color(0xB3, 0xA7, 0x8E);

	// -- Accents (restrained -- see class javadoc's SEMANTIC vs. LOCAL note) --

	/** Important totals, key section headings, highlighted values -- an accent, never the default text color. */
	static final Color ACCENT_GOLD = new Color(0xD4, 0xA9, 0x3A);
	/** A quieter warm accent for secondary emphasis -- distinct from gold, used where a second accent tone avoids "everything is gold." */
	static final Color ACCENT_BRONZE = new Color(0xC9, 0x9A, 0x3C);
	/** Success/positive state -- e.g. a completed Slayer task, a maxed skill. */
	static final Color STATUS_SUCCESS = new Color(0x70, 0xB9, 0x6E);



	/** Preservation (a kill that did not use up a task unit: Bracelet of slaughter). */
	static final Color ACCENT_SAGE = new Color(0x93, 0xAE, 0x7C);
	/** A warning that will cost the player something (a skill companion's DANGER line): red, used sparingly. */
	static final Color STATUS_DANGER = new Color(0xE0, 0x5A, 0x48);
	/** Farming attention (diseased / dead): a warm ember, readable on the tile faces. */
	static final Color FARM_EMBER = new Color(0xE8, 0x86, 0x6A);


	/** A tracked (selected) activity card's face: a muted green the cream text reads on (pass 31). */
	static final Color CARD_SELECTED = new Color(0x33, 0x40, 0x2B);

}
