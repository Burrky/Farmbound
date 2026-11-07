package com.spyglassfarming.travel;

import java.util.List;

/** Test access to the catalogue's entrance pruning rule. */
public final class TransportCatalogAccess
{
	private TransportCatalogAccess()
	{
	}

	public static boolean dominated(Object[] c, List<Object[]> all)
	{
		return CatalogBuilder.dominated(c, all);
	}
}
