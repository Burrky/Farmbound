package com.spyglassfarming.storage;

/**
 * Reads an enum constant saved by name. A missing name, or one this version does not know (a renamed constant or a
 * file written by a newer version), reads as the fallback instead of failing.
 */
public final class SavedEnum
{
	private SavedEnum()
	{
	}

	public static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback)
	{
		if (name == null)
		{
			return fallback;
		}
		try
		{
			return Enum.valueOf(type, name);
		}
		catch (IllegalArgumentException e)
		{
			return fallback;
		}
	}

	/** {@link #parse(Class, String, Enum)} with null as the fallback. */
	public static <E extends Enum<E>> E parse(Class<E> type, String name)
	{
		return parse(type, name, null);
	}
}
