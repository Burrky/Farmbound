package com.spyglassfarming.farming;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One catalogued patch. Its identity is the stable {@link #getPatchId()},
 * backed by the owning region and the transmit varbit: the same varbit means a
 * different patch in every farming area, so the varbit alone identifies nothing.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class FarmingPatchDef
{
	@Getter
	private final String patchId;
	@Getter
	private final String areaName;
	/** "North West", "South" ... or empty when the area has one patch of this type. */
	@Getter
	private final String label;
	@Getter
	private final FarmingPatchType type;
	@Getter
	private final int owningRegionId;
	@Getter
	private final int varbit;

	@Override
	public String toString()
	{
		return patchId;
	}
}
