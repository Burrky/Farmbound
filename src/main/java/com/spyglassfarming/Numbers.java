package com.spyglassfarming;

import java.util.Locale;

/**
 * Whole numbers with thousands separators. Two rules are in use and both are kept as they are: most text always
 * shows {@code 1,234} ({@link #grouped}); the Session, History and Loot totals follow the player's own locale
 * ({@link #groupedForPlayer}, e.g. {@code 1.234} in some regions).
 */
public final class Numbers
{
	private Numbers()
	{
	}

	/** {@code 1,234} whatever the player's locale. */
	public static String grouped(long value)
	{
		return String.format(Locale.ROOT, "%,d", value);
	}

}
