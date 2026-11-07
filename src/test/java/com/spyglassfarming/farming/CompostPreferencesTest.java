package com.spyglassfarming.farming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Pass 31: compost is chosen per patch type (allotments and flowers apart), saved per profile, used by preparation and
 * guidance through the run's options, changeable while a run is open for its pending work, and never an observation.
 */
public class CompostPreferencesTest
{
	static final long T0 = 1_760_000_000_000L;

	static FarmingRunIntent run(List<String> ids, FarmingRunType type, FarmingRunIntent.Options options)
	{
		Map<String, String> plans = new LinkedHashMap<>();
		for (String id : ids)
		{
			plans.put(id, FarmingRunIntent.REPLANT_ANY);
		}
		return new FarmingRunIntent(FarmingRunIntent.SCHEMA, "fr-c", type, T0, T0, FarmingRunIntent.State.ACTIVE, null, null, new java.util.ArrayList<>(ids),
			Collections.emptyList(), Collections.emptyList(), plans, options, Collections.emptyMap());
	}

	@Test
	public void savedPerPatchType_allotmentsAndFlowersApart_andSurvivesSaving()
	{
		FarmingPreferences p = FarmingPreferences.defaults();
		assertFalse(p.isCompostChosen());
		assertEquals(FarmingCompost.NONE, p.compostFor(FarmingPatchType.HERB));
		FarmingPreferences q = p.withCompost(FarmingPatchType.HERB, FarmingCompost.ULTRACOMPOST)
			.withCompost(FarmingPatchType.ALLOTMENT, FarmingCompost.SUPERCOMPOST)
			.withCompost(FarmingPatchType.FLOWER, FarmingCompost.FERTILE_SOIL);
		assertEquals(FarmingCompost.SUPERCOMPOST, q.compostFor(FarmingPatchType.ALLOTMENT));
		assertEquals(FarmingCompost.FERTILE_SOIL, q.compostFor(FarmingPatchType.FLOWER));
		assertNotEquals("a change is a change (the page re-reads it)", p, q);
		FarmingPreferences back = new Gson().fromJson(new Gson().toJson(q), FarmingPreferences.class);
		assertEquals(q, back);
		assertEquals(FarmingCompost.ULTRACOMPOST, back.compostFor(FarmingPatchType.HERB));
		// An older profile (no field) reads as never chosen; an unknown name reads as none.
		FarmingPreferences old = new Gson().fromJson("{\"treeClearing\":\"PAY\"}", FarmingPreferences.class);
		assertFalse(old.isCompostChosen());
		FarmingPreferences odd = new Gson().fromJson("{\"compostByPatch\":{\"HERB\":\"MAGIC\"}}", FarmingPreferences.class);
		assertEquals(FarmingCompost.NONE, odd.compostFor(FarmingPatchType.HERB));
	}

	@Test
	public void changingAChoiceDuringARun_changesOnlyThatType_andTheBountyChoiceKeepsIt()
	{
		FarmingRunIntent.Options o = new FarmingRunIntent.Options("NONE", false).withCompostByPatch(Collections.singletonMap("HERB", "COMPOST"));
		FarmingRunIntent.Options changed = o.withCompostByPatch(Collections.singletonMap("FLOWER", "ULTRACOMPOST"));
		assertEquals("COMPOST", changed.compostFor(FarmingPatchType.HERB));
		assertEquals("ULTRACOMPOST", changed.compostFor(FarmingPatchType.FLOWER));
		assertEquals("NONE", changed.compostFor(FarmingPatchType.TREE));
		assertEquals("ULTRACOMPOST", changed.withBounty(true).compostFor(FarmingPatchType.FLOWER));
	}

}
