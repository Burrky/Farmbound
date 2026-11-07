package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A planting Spyglass actually saw: a same-sequence tick-to-tick change from
 * an empty patch to stage 0 of a crop. A historical fact; it stays true even
 * after the crop it started is gone. Immutable (Gson-friendly).
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class PlantingEvent
{
	@Getter
	private final long at;
	@Getter
	private final FarmingProduce produce;
	@Getter
	private final int raw;
}
