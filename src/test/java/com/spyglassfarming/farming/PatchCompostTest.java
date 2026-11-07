package com.spyglassfarming.farming;

import static com.spyglassfarming.farming.FarmingFixtures.T0;
import static com.spyglassfarming.farming.FarmingFixtures.patch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Compost from the game's own words (canonical §28.14): the messages as they read in Josh's client log (2026-10-22), the
 * planting each belongs to, before or after planting, and the cycle that ends it.
 */
public class PatchCompostTest
{
	private static final long MIN = 60_000L;
	private final Map<String, PatchKnowledge> known = new HashMap<>();

	@Test
	public void theGamesMessages_giveTheKindAndHowItWasSeen()
	{
		PatchCompost.Message a = PatchCompost.parse("You treat the herb patch with supercompost.");
		assertEquals(PatchCompost.Kind.SUPERCOMPOST, a.kind);
		assertEquals(PatchCompost.Source.APPLIED, a.source);
		assertEquals("herb patch", a.patchNoun);
		assertEquals(PatchCompost.Kind.ULTRACOMPOST, PatchCompost.parse("You treat the herb patch with ultracompost.").kind);
		assertEquals(PatchCompost.Kind.COMPOST, PatchCompost.parse("You treat the allotment with compost.").kind);
		PatchCompost.Message fs = PatchCompost.parse("The herb patch has been treated with supercompost.");
		assertEquals(PatchCompost.Source.FERTILE_SOIL, fs.source);
		assertEquals(PatchCompost.Kind.ULTRACOMPOST, PatchCompost.parse("This flower patch has already been treated with ultracompost.").kind);
		PatchCompost.Message inspect = PatchCompost.parse(
			"This is a herb patch. The soil has been treated with ultracompost. The patch has ranarr weed growing in it and is at state 1/5.");
		assertEquals(PatchCompost.Source.INSPECTED, inspect.source);
		assertEquals(PatchCompost.Kind.ULTRACOMPOST, inspect.kind);
		PatchCompost.Message none = PatchCompost.parse("This is an allotment. The soil has not been treated. The patch is empty and weeded.");
		assertEquals("Inspect can confirm the absence", PatchCompost.Kind.NONE, none.kind);
		assertEquals("allotment", none.patchNoun);
		assertNull("plant cure is not compost", PatchCompost.parse("You treat the herb patch with the plant cure."));
		assertNull(PatchCompost.parse("You haven't got any ultracompost to store."));
	}

	@Test
	public void beforePlanting_itBelongsToTheNextPlanting_andTheHarvestEndsIt()
	{
		String id = "herb.falador";
		int empty = 3;
		int seed = FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, FarmingProduce.RANARR, PatchCondition.GROWING, 0);
		int ready = FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, FarmingProduce.RANARR, PatchCondition.HARVESTABLE, -1);
		FarmingKnowledgeFixtures.see(known, id, empty, T0);
		PatchCompost c = PatchCompost.of(PatchCompost.parse("You treat the herb patch with supercompost."), T0 + MIN, known.get(id), patch(id));
		assertNull("applied to a bare patch: the next planting's", c.getCycleFirstSeenAt());
		assertNotNull(c.current(known.get(id)));
		FarmingKnowledgeFixtures.change(known, id, empty, seed, T0 + 2 * MIN);
		assertNotNull("kept when the crop goes in", c.current(known.get(id)));
		FarmingKnowledgeFixtures.change(known, id, seed, ready, T0 + 60 * MIN);
		assertNotNull(c.current(known.get(id)));
		FarmingKnowledgeFixtures.change(known, id, ready, empty, T0 + 61 * MIN);
		assertNull("harvested out: that planting has gone", c.current(known.get(id)));
	}

	@Test
	public void afterPlanting_itBelongsToThatCrop_onlyUntilTheCycleChanges()
	{
		String id = "herb.catherby";
		int seed = FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, FarmingProduce.RANARR, PatchCondition.GROWING, 0);
		int dead = FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, null, PatchCondition.DEAD, -1);
		FarmingKnowledgeFixtures.see(known, id, 3, T0);
		FarmingKnowledgeFixtures.change(known, id, 3, seed, T0 + MIN);
		PatchCompost c = PatchCompost.of(PatchCompost.parse("You treat the herb patch with ultracompost."), T0 + 2 * MIN, known.get(id), patch(id));
		assertNotNull(c.getCycleFirstSeenAt());
		assertEquals(PatchCompost.Kind.ULTRACOMPOST, c.current(known.get(id)).getKind());
		FarmingKnowledgeFixtures.change(known, id, seed, dead, T0 + 30 * MIN);
		FarmingKnowledgeFixtures.change(known, id, dead, 3, T0 + 31 * MIN); // cleared
		assertNull(c.current(known.get(id)));
	}

	@Test
	public void theMessageMustFitThePatchTheClickChose()
	{
		assertTrue(FarmingCaptureAdapter.names("herb patch", patch("herb.falador")));
		assertTrue(FarmingCaptureAdapter.names("allotment", patch("allotment.falador.nw")));
		assertTrue(FarmingCaptureAdapter.names("calquat tree patch", patch("calquat.kastori")));
		assertFalse("a herb message never credits the allotment", FarmingCaptureAdapter.names("herb patch", patch("allotment.falador.nw")));
		FarmingCaptureAdapter.PendingCompost pc = new FarmingCaptureAdapter.PendingCompost("herb.falador", 3058, 3311, 3059, 3312, 0, T0);
		assertTrue(pc.beside(new net.runelite.api.coords.WorldPoint(3060, 3311, 0)));
		assertFalse("not beside it: not credited", pc.beside(new net.runelite.api.coords.WorldPoint(3063, 3311, 0)));
	}

}
