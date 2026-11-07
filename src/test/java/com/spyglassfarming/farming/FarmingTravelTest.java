package com.spyglassfarming.farming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.spyglassfarming.account.Known;
import com.spyglassfarming.travel.TransportCatalog;
import com.spyglassfarming.travel.TransportDef;
import com.spyglassfarming.account.EvidenceSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Farming's destinations and domain evidence for the generic transport core. */
public class FarmingTravelTest
{
	private static final long T = 1_800_000_000_000L;
	private static final String PORT_SARIM = "spirit_tree.port_sarim";
	private static final String KEY = FarmingTravel.EVIDENCE_PREFIX + "port_sarim";

	private static int raw(PatchCondition c)
	{
		return FarmingKnowledgeFixtures.raw(FarmingPatchType.SPIRIT_TREE, FarmingProduce.SPIRIT_TREE, c, -1);
	}

	private static Known known(Map<String, PatchKnowledge> k)
	{
		return FarmingTravel.evidence(k, T + 7_200_000L).get(KEY);
	}

	@Test
	public void spiritTreeEvidence_comesFromObservationOnly()
	{
		Map<String, PatchKnowledge> k = new HashMap<>();
		assertEquals("never observed", Known.UNKNOWN, known(k));
		FarmingKnowledgeFixtures.see(k, PORT_SARIM, raw(PatchCondition.GROWING), T);
		assertEquals("growing when last seen: maybe grown by now, not assumed", Known.UNKNOWN, known(k));
		FarmingKnowledgeFixtures.change(k, PORT_SARIM, raw(PatchCondition.GROWING), raw(PatchCondition.GROWN), T + 1000);
		assertEquals(Known.YES, known(k));
		assertTrue(FarmingTravel.evidence(k, T + 7_200_000L).explain(KEY).contains("grown when last seen 1h 59m ago"));
		FarmingKnowledgeFixtures.change(k, PORT_SARIM, raw(PatchCondition.GROWN), raw(PatchCondition.CHECK_HEALTH), T + 2000);
		assertEquals(Known.UNKNOWN, known(k));
		Map<String, PatchKnowledge> dead = new HashMap<>();
		FarmingKnowledgeFixtures.see(dead, PORT_SARIM, raw(PatchCondition.DEAD), T);
		assertEquals(Known.NO, known(dead));
		assertEquals(Known.UNKNOWN, FarmingTravel.evidence(null, T).get(KEY));
		assertEquals(Known.UNKNOWN, FarmingTravel.evidence(k, T).get("other.key"));
	}

	@Test
	public void evidenceNeverExpiresWithTime()
	{
		Map<String, PatchKnowledge> k = new HashMap<>();
		FarmingKnowledgeFixtures.see(k, PORT_SARIM, raw(PatchCondition.GROWN), T);
		EvidenceSource yearLater = FarmingTravel.evidence(k, T + 365L * 24 * 3_600_000L);
		assertEquals(Known.YES, yearLater.get(KEY));
	}

	@Test
	public void everyStop_hasARoute_fromRouteData_andTheGreatConchOnlyByItsRing()
	{
		FarmingPatchCatalog patches = FarmingPatchCatalog.standard();
		java.util.TreeSet<String> uncovered = new java.util.TreeSet<>();
		for (FarmingPatchDef p : patches.allPatches())
		{
			if (FarmingTravel.forStop(patches, p.getPatchId()).isEmpty())
			{
				uncovered.add(p.getPatchId());
			}
		}
		assertTrue("every patch has a walk from some catalogue arrival: " + uncovered, uncovered.isEmpty());
		assertEquals("the Great Conch is an island reached by fairy ring CJQ",
			java.util.Arrays.asList("fairy_ring.cjq", "poh.spell.fairy_ring.cjq", "poh.tab.fairy_ring.cjq"),
			FarmingTravel.forStop(patches, "calquat.great_conch"));
		List<String> falador = FarmingTravel.forStop(patches, "allotment.falador.nw");
		assertTrue("an allotments-only Falador visit shares the herb patch's teleports: " + falador, falador.contains("spell.falador"));
		assertTrue("cheapest first", falador.indexOf("explorers_ring.cabbage_patch") < falador.indexOf("spell.varrock"));
	}

}
