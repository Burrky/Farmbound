package com.spyglassfarming.travel;

import static com.spyglassfarming.account.AccountFixtures.NOW;
import static com.spyglassfarming.account.AccountFixtures.bank;
import static com.spyglassfarming.account.AccountFixtures.items;
import static com.spyglassfarming.account.AccountFixtures.maxed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.EvidenceSource;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The cost-only choice for preparation (canonical §14.2, Phase H). */
public class TravelPlannerTest
{
	private static final TransportCatalog CATALOG = TransportCatalogTest.CATALOG;

	private static List<TransportAvailability> eval(AccountCapability cap, String... ids)
	{
		List<TransportAvailability> out = new ArrayList<>();
		for (String id : ids)
		{
			out.add(TransportEvaluator.evaluate(CATALOG.get(id), cap, EvidenceSource.NONE, NOW));
		}
		return out;
	}

	@Test
	public void housePreference_choosesAUsableHouseJourney_andFallsBackWhenNoneIsUsable()
	{
		// Draynor: the amulet of glory and the house's ornate jewellery box land on the same tile.
		EvidenceSource box = new EvidenceSource()
		{
			@Override
			public com.spyglassfarming.account.Known get(String key)
			{
				return key.startsWith("poh.jewellery_box") ? com.spyglassfarming.account.Known.YES : com.spyglassfarming.account.Known.UNKNOWN;
			}

			@Override
			public String explain(String key)
			{
				return "test";
			}
		};
		AccountCapability cap = maxed().worn(items(ItemID.AMULET_OF_GLORY_6, 1)).inventory(items(ItemID.POH_TABLET_TELEPORTTOHOUSE, 5)).build();
		List<TransportAvailability> c = new ArrayList<>();
		c.add(TransportEvaluator.evaluate(CATALOG.get("glory.draynor_village"), cap, box, NOW));
		c.add(TransportEvaluator.evaluate(CATALOG.get("poh.tab.jewellery.draynor_village"), cap, box, NOW));
		assertEquals("fastest: the carried amulet (no way into the house first)", "glory.draynor_village",
			TravelPlanner.chooseByJourney(t -> 10, c, false).getChosen().getTransport().id);
		TravelPlanner.Choice house = TravelPlanner.chooseByJourney(t -> 10, c, true);
		assertEquals("prefer my house: the jewellery box", "poh.tab.jewellery.draynor_village", house.getChosen().getTransport().id);
		assertEquals("the carried amulet is still named as the other way", "glory.draynor_village", house.getAlternatives().get(0).getTransport().id);
		assertEquals("preferred and usable: the house journey wins even when slower", "poh.tab.jewellery.draynor_village",
			TravelPlanner.chooseByJourney(t -> TravelPlanner.isHouse(t) ? 500 : 10, c, true).getChosen().getTransport().id);
		assertEquals("its way from the arrival closed for this account: not usable, the other way", "glory.draynor_village",
			TravelPlanner.chooseByJourney(t -> TravelPlanner.isHouse(t) ? -1 : 10, c, true).getChosen().getTransport().id);
		// The box not seen in this house: the preference never makes it usable.
		List<TransportAvailability> unseen = new ArrayList<>();
		unseen.add(TransportEvaluator.evaluate(CATALOG.get("glory.draynor_village"), cap, EvidenceSource.NONE, NOW));
		unseen.add(TransportEvaluator.evaluate(CATALOG.get("poh.tab.jewellery.draynor_village"), cap, EvidenceSource.NONE, NOW));
		assertEquals("an unconfirmed facility: the fastest usable", "glory.draynor_village", TravelPlanner.chooseByJourney(t -> 10, unseen, true).getChosen().getTransport().id);
		// No way into the house (no tablet, cape or spell): not usable either.
		AccountCapability noTabs = maxed().worn(items(ItemID.AMULET_OF_GLORY_6, 1)).build();
		List<TransportAvailability> noEntry = new ArrayList<>();
		noEntry.add(TransportEvaluator.evaluate(CATALOG.get("glory.draynor_village"), noTabs, box, NOW));
		noEntry.add(TransportEvaluator.evaluate(CATALOG.get("poh.tab.jewellery.draynor_village"), noTabs, box, NOW));
		assertEquals("no way into the house: the fastest usable", "glory.draynor_village", TravelPlanner.chooseByJourney(t -> 10, noEntry, true).getChosen().getTransport().id);
	}
}
