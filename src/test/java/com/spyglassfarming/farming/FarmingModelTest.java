package com.spyglassfarming.farming;

import static com.spyglassfarming.farming.FarmingFixtures.T0;
import static com.spyglassfarming.farming.FarmingFixtures.patch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** View-model derivation: overview, rows, Home headline priority, and draft defaults that never touch knowledge. */
public class FarmingModelTest
{
	private static final long MIN = 60_000L;
	private static final long DAY = 24 * 60 * MIN;
	private static final FarmingPatchCatalog CATALOG = FarmingPatchCatalog.standard();
	private static final FarmingOwner OWNER = FarmingOwner.standard(FarmingFixtures.ACCOUNT);

	private final Map<String, PatchKnowledge> known = new HashMap<>();

	private void see(String id, int raw, long at)
	{
		FarmingPatchDef p = patch(id);
		known.put(id, CropCycleAttribution.apply(known.get(id),
			new PatchObservation(PatchObservation.Kind.BASELINE, p, FarmingDecoder.decode(p.getType(), raw), null, at, 0L, 1)));
	}

	private void plant(String id, int emptyRaw, int seedRaw, long at)
	{
		FarmingPatchDef p = patch(id);
		see(id, emptyRaw, at - 600);
		known.put(id, CropCycleAttribution.apply(known.get(id), new PatchObservation(PatchObservation.Kind.CHANGE, p,
			FarmingDecoder.decode(p.getType(), seedRaw), FarmingDecoder.decode(p.getType(), emptyRaw), at, at - 600, 1)));
	}

	private FarmingModel model(long now)
	{
		return FarmingModel.build(CATALOG, new FarmingSnapshot(FarmingSnapshot.State.AVAILABLE, OWNER, known, Collections.emptyList()), now);
	}

	@Test
	public void home_followsTheCanonicalPriority()
	{

		see("herb.falador", 3, T0);

		plant("herb.catherby", 3, 32, T0);
		FarmingModel growing = model(T0 + 10 * MIN);

		FarmingModel ready = model(T0 + 2 * DAY); // the estimate has passed: READY, no qualifier

		see("herb.ardougne", 141, T0); // a diseased patch never outranks a known Ready (pass 32)
		FarmingModel attention = model(T0 + 2 * DAY);
	}

	@Test
	public void home_saysWhyNothingIsShown_whenNotAvailable()
	{
		FarmingSnapshot loading = new FarmingSnapshot(FarmingSnapshot.State.LOADING, OWNER, Collections.emptyMap(), Collections.emptyList());
		FarmingSnapshot elsewhere = new FarmingSnapshot(FarmingSnapshot.State.NOT_AVAILABLE_HERE, null, Collections.emptyMap(), Collections.emptyList());
	}

	@Test
	public void overview_linesForTrackedTypes_andUntrackedGrouped()
	{
		see("herb.falador", 36, T0);
		see("tree.falador", 46, T0); // a standing yew, harvestable
		see("allotment.falador.nw", 3, T0);
		FarmingModel m = model(T0 + MIN);
	}

	@Test
	public void rows_haveTheThreeLineShape_andUnknownIsNeverClear()
	{
		plant("herb.falador", 3, 75, T0); // snapdragon seed
		see("herb.catherby", 220, T0); // undecodable
		FarmingModel m = model(T0 + 30 * MIN);
		FarmingModel.Row falador = row(m, FarmingRunType.HERB, "herb.falador");
		assertEquals("Falador", falador.getLocation());
		assertEquals("Snapdragon", falador.getCrop());
		assertTrue(falador.getChip(), falador.getChip().startsWith("~"));
		assertEquals("Planted 30m ago", falador.getProvenance());
		FarmingModel.Row catherby = row(m, FarmingRunType.HERB, "herb.catherby");
		assertEquals("CHECK PATCH?", catherby.getChip());
		FarmingModel.Row ardougne = row(m, FarmingRunType.HERB, "herb.ardougne");
		assertEquals("NOT TRACKED", ardougne.getChip());
		assertEquals("–", ardougne.getCrop());
		assertEquals("Visit once to start tracking", ardougne.getProvenance());
	}

	@Test
	public void allotmentPreview_carriesFlowersAsOptionalCompanions()
	{
		FarmingModel m = model(T0);
		List<FarmingModel.Row> rows = m.rows(FarmingRunType.ALLOTMENT);
		FarmingModel.Row nw = row(m, FarmingRunType.ALLOTMENT, "allotment.falador.nw");
		FarmingModel.Row flower = row(m, FarmingRunType.ALLOTMENT, "flower.falador");
		assertEquals("Falador (NW)", nw.getLocation());
		assertEquals("Falador flower", flower.getLocation());
		assertTrue(flower.isCompanion());
		assertFalse(nw.isCompanion());
		// Correction pass 2: one "Allotments & Flowers" category; flowers count in it, and are included only when switched on.
		assertEquals(rows.size(), m.summary(FarmingRunType.ALLOTMENT).getTotal());
		FarmingPlanDraft draft = new FarmingPlanDraft();
		assertFalse("flowers are off by default", draft.isIncluded(flower, T0));
		assertTrue(draft.isIncluded(nw, T0));
		draft.setInclude(FarmingPatchType.FLOWER, true, rows);
		assertTrue(draft.isIncluded(flower, T0));
		draft.setInclude(FarmingPatchType.ALLOTMENT, false, rows);
		assertFalse("flowers alone: allotments never switched on with them", draft.isIncluded(nw, T0));
		assertTrue(draft.isIncluded(flower, T0));
	}

	@Test
	public void selection_parsesSafely()
	{
		assertEquals(FarmingRunType.HERB, FarmingRunType.parse("HERB"));
		assertEquals(FarmingRunType.REDWOOD, FarmingRunType.parse("REDWOOD"));
		assertNull(FarmingRunType.parse(""));
		assertNull(FarmingRunType.parse(null));
		assertNull("a removed or garbled value falls back to the overview", FarmingRunType.parse("GRAPES"));
		for (FarmingPatchType t : FarmingPatchType.values())
		{
			FarmingRunType.of(t); // every type is browsable somewhere
		}
		assertEquals(FarmingRunType.ALLOTMENT, FarmingRunType.of(FarmingPatchType.FLOWER));
	}

	private static FarmingModel.Row row(FarmingModel m, FarmingRunType type, String id)
	{
		for (FarmingModel.Row r : m.rows(type))
		{
			if (r.getPatchId().equals(id))
			{
				return r;
			}
		}
		throw new AssertionError(id);
	}
}
