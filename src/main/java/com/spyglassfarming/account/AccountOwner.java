package com.spyglassfarming.account;

import java.util.Objects;
import lombok.Getter;

/**
 * Whose account knowledge this is: an account hash plus a RuneScape profile.
 * Account capability is collected and persisted for the STANDARD profile only
 * (canonical §22); evidence from one owner is never shown, merged or written
 * under another.
 */
public final class AccountOwner
{
	public static final String STANDARD_PROFILE = "standard";

	@Getter
	private final long accountHash;
	@Getter
	private final String profileKey;

	public AccountOwner(long accountHash, String profileKey)
	{
		this.accountHash = accountHash;
		this.profileKey = Objects.requireNonNull(profileKey);
	}

	public static AccountOwner standard(long accountHash)
	{
		return new AccountOwner(accountHash, STANDARD_PROFILE);
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof AccountOwner))
		{
			return false;
		}
		AccountOwner other = (AccountOwner) o;
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
