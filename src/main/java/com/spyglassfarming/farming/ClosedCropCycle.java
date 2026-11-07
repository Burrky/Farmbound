package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A crop cycle that a native observation ended (the patch was seen cleared:
 * empty or weeds), kept as history. Immutable (Gson-friendly).
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class ClosedCropCycle
{
	@Getter
	private final CropCycle cycle;
	@Getter
	private final long closedAt;
	@Getter
	private final PatchCondition closedBy;
}
