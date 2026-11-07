package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What a patch shows right now: the result of {@link FarmingProjection}.
 * Immutable, computed at presentation time and never written back.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class PatchStatus
{
	/** The single player-facing state (canonical §3.2 / §18.4). */
	public enum Display
	{
		/** Observed harvestable / check-health, or the estimated growth time has passed. */
		READY,
		/** Growing, with an estimate ({@link #getReadyAt()}). */
		GROWING_TIMED,
		/** Growing, but nothing defensible to time it by (e.g. anima). */
		GROWING,
		/** Fully grown with nothing to harvest (a checked spirit tree, a scarecrow). */
		GROWN,
		DISEASED,
		DEAD,
		/** Observed empty or weeds. */
		CLEAR,
		/** Never observed on this profile. */
		NOT_TRACKED,
		/** The latest reading could not be decoded: Spyglass refuses to guess. */
		CHECK_PATCH
	}

	/** Where a READY came from. Internal: never player text. */
	public enum ReadySource
	{
		OBSERVED,
		ESTIMATED
	}

	/** Which provenance line applies (canonical §3.2). */
	public enum Provenance
	{
		NONE,
		SEEN_JUST_NOW,
		PLANTED,
		SEEN_GROWING,
		LAST_SEEN,
		LAST_SEEN_DISEASED,
		LAST_SEEN_DEAD
	}

	@Getter
	private final Display display;
	/** Null unless READY. */
	@Getter
	private final ReadySource readySource;
	/** The crop in the patch (or last known before an unreadable value); null when none or unknown. */
	@Getter
	private final FarmingProduce produce;
	/** A crop is there but its kind is unknown (a dead herb seen after another crop). */
	@Getter
	private final boolean produceUnknown;
	/** The earliest the crop can be ready (epoch ms); null when not estimated. */
	@Getter
	private final Long readyEarliest;
	/** When the estimate turns READY (the latest possible ready time, epoch ms); null when not estimated. */
	@Getter
	private final Long readyAt;
	@Getter
	private final Provenance provenance;
	@Getter
	private final long provenanceAt;
	/** The last native observation of this patch (0 when never seen). */
	@Getter
	private final long lastSeenAt;

	public boolean isReady()
	{
		return display == Display.READY;
	}

	public boolean isTracked()
	{
		return display != Display.NOT_TRACKED;
	}
}
