package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One raw patch value the observer recorded on a settled tick. Immutable.
 *
 * <ul>
 *   <li>{@link Kind#BASELINE}: the first settled read of this patch since the
 *   last continuity break. It says what the patch is NOW; it proves nothing
 *   about when it changed, so it can never establish a planting time or an
 *   exact growth tick.</li>
 *   <li>{@link Kind#CHANGE}: the value differs from the read on the
 *   immediately preceding settled tick of the same sequence, so the change
 *   happened between those two ticks.</li>
 * </ul>
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class PatchObservation
{
	public enum Kind
	{
		BASELINE,
		CHANGE
	}

	@Getter
	private final Kind kind;
	@Getter
	private final FarmingPatchDef patch;
	@Getter
	private final PatchReading reading;
	/** CHANGE only: the reading on the previous tick. */
	@Getter
	private final PatchReading previous;
	@Getter
	private final long observedAt;
	/** CHANGE only: when the previous reading was taken (one tick earlier). */
	@Getter
	private final long previousObservedAt;
	@Getter
	private final long sequence;

	/** A same-sequence, tick-to-tick change: the only kind that can prove planting or a growth tick. */
	public boolean isContinuousChange()
	{
		return kind == Kind.CHANGE;
	}

	@Override
	public String toString()
	{
		return kind + " " + patch.getPatchId() + " " + (previous == null ? "" : previous.getRaw() + "->") + reading.getRaw() + " seq" + sequence;
	}
}
