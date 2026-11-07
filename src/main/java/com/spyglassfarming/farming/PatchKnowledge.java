package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Everything persisted about one patch for one owner. Immutable (Gson-friendly).
 *
 * <ul>
 *   <li>{@code lastRaw} / {@code decoderVersion} / {@code lastObservedAt}: the
 *   most recent BASELINE or CHANGE (the raw value is the record).</li>
 *   <li>{@code lastSeenAt}: freshness, also advanced by unchanged settled
 *   reads, which are never logged.</li>
 *   <li>{@code cycle}: the crop currently attributed to the patch, or null
 *   when none is known. Retained across time away and new sequences; ended
 *   only by a native observation (cleared, or a different / regressed crop).
 *   A dead or diseased crop keeps its cycle; an UNKNOWN reading erases
 *   nothing.</li>
 *   <li>{@code lastClosedCycle}: the most recent cycle a native observation
 *   closed (seen cleared), kept as history.</li>
 *   <li>{@code lastObservedPlanting}: the latest planting actually seen,
 *   kept as history even when it can no longer be attributed to the crop now
 *   present.</li>
 *   <li>{@code decodeStale}: a newer decoder could not decode the stored raw
 *   value; projections must treat this patch as stale.</li>
 * </ul>
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class PatchKnowledge
{
	@Getter
	private final String patchId;
	@Getter
	private final int region;
	@Getter
	private final int varbit;
	@Getter
	private final int lastRaw;
	@Getter
	private final int decoderVersion;
	@Getter
	private final long lastObservedAt;
	@Getter
	private final long lastSeenAt;
	@Getter
	private final CropCycle cycle;
	@Getter
	private final PlantingEvent lastObservedPlanting;
	@Getter
	private final ClosedCropCycle lastClosedCycle;
	@Getter
	private final boolean decodeStale;

	PatchKnowledge seenUnchanged(long at)
	{
		return new PatchKnowledge(patchId, region, varbit, lastRaw, decoderVersion, lastObservedAt, Math.max(lastSeenAt, at),
			cycle == null ? null : cycle.touched(at), lastObservedPlanting, lastClosedCycle, decodeStale);
	}

	PatchKnowledge withDecoder(int version, CropCycle newCycle, boolean stale)
	{
		return new PatchKnowledge(patchId, region, varbit, lastRaw, version, lastObservedAt, lastSeenAt, newCycle, lastObservedPlanting, lastClosedCycle, stale);
	}
}
