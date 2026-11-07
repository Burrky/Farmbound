package com.spyglassfarming.prep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** A request's needs evaluated against one account at one moment. */
public final class PreparationState
{
	@Getter
	private final PreparationRequest request;
	@Getter
	private final List<NeedResult> results;

	PreparationState(PreparationRequest request, List<NeedResult> results)
	{
		this.request = request;
		this.results = Collections.unmodifiableList(results);
	}

	/** Overall readiness, given how many things the consumer could not confirm (unknown charges, unconfirmed access, no usable transport). */
	public Readiness readiness(int unverified)
	{
		if (requiredLeft() > 0)
		{
			return Readiness.MISSING;
		}
		return unverified > 0 ? Readiness.UNVERIFIED : Readiness.CONFIRMED;
	}

	public NeedResult result(String key)
	{
		for (NeedResult r : results)
		{
			if (r.getNeed().getKey().equals(key))
			{
				return r;
			}
		}
		return null;
	}

	public int requiredLeft()
	{
		int n = 0;
		for (NeedResult r : results)
		{
			if (r.getNeed().getNecessity() == ItemNeed.Necessity.REQUIRED && !r.isSatisfied())
			{
				n++;
			}
		}
		return n;
	}

}
