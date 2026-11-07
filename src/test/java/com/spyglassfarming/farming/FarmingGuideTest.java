package com.spyglassfarming.farming;

import static com.spyglassfarming.account.AccountFixtures.NOW;
import static com.spyglassfarming.account.AccountFixtures.maxed;
import static com.spyglassfarming.farming.FarmingFixtures.patch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.EvidenceSource;
import com.spyglassfarming.travel.TransportCatalog;
import com.spyglassfarming.travel.TransportEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Adaptive guidance: follows the stop the player is actually at, sticks to its choice, re-targets after a teleport. */
public class FarmingGuideTest
{
	private static final FarmingPatchCatalog CATALOG = FarmingPatchCatalog.standard();
	private static final TransportCatalog TRANSPORTS = com.spyglassfarming.travel.TestTransports.CATALOG;
	private static final List<String> IDS = Arrays.asList("herb.falador", "herb.catherby", "herb.ardougne");

	private static FarmingRunIntent intent()
	{
		Map<String, String> plans = new LinkedHashMap<>();
		Map<String, FarmingRunIntent.Baseline> baseline = new LinkedHashMap<>();
		for (String id : IDS)
		{
			plans.put(id, FarmingRunIntent.REPLANT_ANY);
			baseline.put(id, FarmingRunIntent.Baseline.of(patch(id), null));
		}
		return new FarmingRunIntent(FarmingRunIntent.SCHEMA, "fr-guide", FarmingRunType.HERB, NOW, NOW, FarmingRunIntent.State.ACTIVE, null, null,
			new ArrayList<>(IDS), Collections.emptyList(), Collections.emptyList(), plans, new FarmingRunIntent.Options(null, false), baseline);
	}

	private static List<FarmingPreparation.SitePlan> sites(AccountCapability cap)
	{
		return FarmingPreparation.plan(intent().withPreparing(true), CATALOG, cap, TransportEvaluator.evaluateAll(TRANSPORTS, cap, EvidenceSource.NONE, NOW),
			EvidenceSource.NONE).getSites();
	}

	private static FarmingRunView view(String... here)
	{
		FarmingSnapshot snap = new FarmingSnapshot(FarmingSnapshot.State.AVAILABLE, FarmingOwner.standard(FarmingFixtures.ACCOUNT), new LinkedHashMap<>(),
			Collections.emptyList(), intent(), new HashSet<>(Arrays.asList(here)));
		return FarmingModel.build(CATALOG, snap, NOW).getRun();
	}

	private static FarmingPreparation.SitePlan site(List<FarmingPreparation.SitePlan> sites, String area)
	{
		return sites.stream().filter(s -> s.getSite().getName().startsWith(area)).findFirst().orElseThrow(AssertionError::new);
	}

	private static FarmingGuide.Position at(FarmingPreparation.SitePlan s, int dx)
	{
		return new FarmingGuide.Position(s.getSite().getTile().x + dx, s.getSite().getTile().y, s.getSite().getTile().plane);
	}

	@Test
	public void atAPatch_theObjectiveIsTheWorkThere_inWhateverOrder()
	{
		List<FarmingPreparation.SitePlan> sites = sites(maxed().build());
		FarmingGuide.Objective o = FarmingGuide.resolve(view("herb.ardougne"), sites, at(site(sites, "Ardougne"), 0), null, "Falador · herb",
			FarmingPreferences.defaults());
		assertEquals(FarmingGuide.Kind.ACT, o.getKind());
		assertTrue(o.getStop(), o.getStop().startsWith("Ardougne"));
		assertTrue(o.getChecklist().toString(), o.getChecklist().stream().anyMatch(l -> l.startsWith("○ ")));
		assertEquals("one current action at the patch", FarmingAction.Stage.ACT, o.getAction().getStage());
		assertEquals(FarmingAction.Target.Kind.PATCH, o.getAction().getTarget().kind);
		assertNull("nothing to teleport with while acting", o.getTransport());
	}

	@Test
	public void nearTheNextStop_itWalks_farAway_itNamesTheTeleport()
	{
		List<FarmingPreparation.SitePlan> sites = sites(maxed().inventory(com.spyglassfarming.account.AccountFixtures.items(8009, 5, 8010, 5, 8011, 5)).build());
		FarmingPreparation.SitePlan first = sites.get(0);
		FarmingGuide.Objective near = FarmingGuide.resolve(view(), sites, at(first, 20), null, null, FarmingPreferences.defaults());
		assertEquals(FarmingGuide.Kind.TRAVEL, near.getKind());
		assertEquals(first.getSite().getName(), near.getStop());
		assertTrue(near.getChecklist().get(0), near.getChecklist().get(0).startsWith("Walk there"));
		FarmingGuide.Objective far = FarmingGuide.resolve(view(), sites, new FarmingGuide.Position(3200, 3200, 0), null, null,
			FarmingPreferences.defaults());
		assertEquals(FarmingGuide.Kind.TRAVEL, far.getKind());
		assertNotNull("a teleport is suggested and outlined", far.getTransport());
		assertTrue(far.getChecklist().get(0), far.getChecklist().get(0).startsWith("Use: "));
	}

	/**
	 * Standalone (2026-11-06): the next stop is the first left in the run's order (the player's own, shown in preparation), not
	 * the nearest one, so NEXT and preparation never disagree. A stop the player is standing at is still worked there.
	 */
	@Test
	public void theNextStop_isTheFirstInTheRunsOrder_butAStopThePlayerIsAtIsWorkedThere()
	{
		List<FarmingPreparation.SitePlan> sites = sites(maxed().build());
		FarmingPreparation.SitePlan first = sites.get(0);
		FarmingPreparation.SitePlan other = sites.get(sites.size() - 1);
		assertNotEquals(first.getSite().getName(), other.getSite().getName());
		// Near the other stop (but not at it), even after a teleport there: travel to the first stop in the order.
		FarmingGuide.Objective o = FarmingGuide.resolve(view(), sites, at(other, 40), at(first, 0), first.getSite().getName(),
			FarmingPreferences.defaults());
		assertEquals(FarmingGuide.Kind.TRAVEL, o.getKind());
		assertEquals(first.getSite().getName(), o.getStop());
		// Standing at the other stop's patch: it is worked where the player is.
		FarmingGuide.Objective here = FarmingGuide.resolve(view(other.getSite().getPatchIds().get(0)), sites, at(other, 0), null, null,
			FarmingPreferences.defaults());
		assertEquals(FarmingGuide.Kind.ACT, here.getKind());
		assertEquals(other.getSite().getName(), here.getStop());
	}

	@Test
	public void atThePatch_theChecklistIsTheWork_neverInventoryOrBankSteps()
	{
		List<FarmingPreparation.SitePlan> sites = sites(maxed().build());
		FarmingGuide.Objective o = FarmingGuide.resolve(view("herb.catherby"), sites, at(site(sites, "Catherby"), 0), null, null,
			FarmingPreferences.defaults(), FarmingPatchCatalog.standard());
		assertEquals(FarmingGuide.Kind.ACT, o.getKind());
		for (String l : o.getChecklist())
		{
			// A lone patch needs no "Herb:" prefix; nothing about the bank or the inventory.
			assertTrue(l, (l.startsWith("○ ") || l.startsWith("✓ ")) && !l.startsWith("○ Herb:") && !l.toLowerCase().contains("bank"));
		}
	}

	private static FarmingRunView view(List<String> current, String... here)
	{
		FarmingSnapshot snap = new FarmingSnapshot(FarmingSnapshot.State.AVAILABLE, FarmingOwner.standard(FarmingFixtures.ACCOUNT), new LinkedHashMap<>(),
			Collections.emptyList(), intent(), new HashSet<>(Arrays.asList(here)), current);
		return FarmingModel.build(CATALOG, snap, NOW).getRun();
	}

	private static List<String> order(FarmingRunView run)
	{
		List<String> out = new ArrayList<>();
		run.getLines().forEach(l -> out.add(l.getPatchId()));
		return out;
	}

	@Test
	public void oneOrder_theStopTheGuideIsOnLeads_thenThePlannedOrder()
	{
		// Live finding 2026-10-21: the guide said Catherby (the player was there) while To do listed it last.
		List<FarmingPreparation.SitePlan> sites = sites(maxed().build());
		FarmingPreparation.SitePlan cath = site(sites, "Catherby");
		FarmingGuide.Objective o = FarmingGuide.resolve(view("herb.catherby"), sites, at(cath, 0), null, null, FarmingPreferences.defaults());
		assertEquals(cath.getSite().getName(), o.getStop());
		// Guidance publishes that stop; every surface built from the snapshot leads with it.
		FarmingRunView run = view(cath.getSite().getPatchIds(), "herb.catherby");
		assertEquals(Arrays.asList("herb.catherby", "herb.falador", "herb.ardougne"), order(run));
		assertTrue("highlighted", run.getLines().get(0).isCurrent());
		assertTrue(run.getLines().stream().skip(1).noneMatch(FarmingRunView.PatchLine::isCurrent));
		assertEquals("Catherby \u00b7 Herb", run.getNextTitle());
		// Travelling to it (not there yet): still first, the NEXT card names it.
		FarmingRunView travelling = view(cath.getSite().getPatchIds());
		assertEquals("herb.catherby", order(travelling).get(0));
		assertEquals("the place and patch type, travelling or there", "Catherby \u00b7 Herb", travelling.getNextTitle());
		// No current stop: the planned order, unchanged.
		assertEquals(IDS, order(view(Collections.emptyList())));
	}

	@Test
	public void aGroupedStop_keepsItsPatchesApart_inTheStopsActionOrder()
	{
		FarmingRunView run = view(Arrays.asList("herb.ardougne", "herb.falador"));
		assertEquals("each patch keeps its own line, in the stop's order, then the plan", Arrays.asList("herb.ardougne", "herb.falador", "herb.catherby"),
			order(run));
		assertTrue(run.getLines().get(0).isCurrent() && run.getLines().get(1).isCurrent() && !run.getLines().get(2).isCurrent());
	}
}
