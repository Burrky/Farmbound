package com.spyglassfarming.farming;

import java.util.Objects;
import lombok.Getter;

/**
 * Whose Farming state something is: an account on a RuneScape profile. Every
 * load, write and in-memory state carries its owner so it can never be applied
 * to, or written for, another account or profile. Only the STANDARD profile
 * is persisted in this era (canonical architecture §22).
 */
public final class FarmingOwner
{
	public static final String STANDARD_PROFILE = "standard";

	@Getter
	private final long accountHash;
	@Getter
	private final String profileKey;

	public FarmingOwner(long accountHash, String profileKey)
	{
		this.accountHash = accountHash;
		this.profileKey = Objects.requireNonNull(profileKey);
	}

	public static FarmingOwner standard(long accountHash)
	{
		return new FarmingOwner(accountHash, STANDARD_PROFILE);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof FarmingOwner))
		{
			return false;
		}
		FarmingOwner other = (FarmingOwner) o;
		return accountHash == other.accountHash && profileKey.equals(other.profileKey);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(accountHash, profileKey);
	}

	@Override
	public String toString()
	{
		return accountHash + "/" + profileKey;
	}
}
