package com.spyglassfarming.farming;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** An immutable world position (x, y, plane) as the observer sees it; map region derived the way the game does. */
@AllArgsConstructor
public final class FarmingLocation
{
	@Getter
	private final int x;
	@Getter
	private final int y;
	@Getter
	private final int plane;

	/** {@code ((x >> 6) << 8) | (y >> 6)}, as {@code WorldPoint.getRegionID()}. */
	public int getRegionId()
	{
		return ((x >> 6) << 8) | (y >> 6);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof FarmingLocation))
		{
			return false;
		}
		FarmingLocation l = (FarmingLocation) o;
		return x == l.x && y == l.y && plane == l.plane;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, plane);
	}

	@Override
	public String toString()
	{
		return "(" + x + "," + y + "," + plane + ")";
	}
}
