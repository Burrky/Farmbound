package com.spyglassfarming.prep;

import static com.spyglassfarming.account.AccountFixtures.NOW;
import static com.spyglassfarming.account.AccountFixtures.bank;
import static com.spyglassfarming.account.AccountFixtures.base;
import static com.spyglassfarming.account.AccountFixtures.group;
import static com.spyglassfarming.account.AccountFixtures.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.AccountProfileGate;
import com.spyglassfarming.account.StorageEvidence;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Need statuses (canonical §8.3, Phase H): carried vs bank vs elsewhere vs not found vs unknown, with runes and pouch rules. */
public class PreparationEvaluatorTest
{
	private static final long HOUR = 3_600_000L;

	private static ItemNeed seeds(int n)
	{
		return new ItemNeed("seed.ranarr", "Ranarr seed", "Supplies", new int[]{ItemID.RANARR_SEED}, n, ItemNeed.Necessity.REQUIRED,
			ItemNeed.SatisfiedBy.INVENTORY_ONLY, "6 patches");
	}

	private static ItemNeed law(int n)
	{
		return new ItemNeed("rune.law", "Law rune", "Travel", new int[]{ItemID.LAWRUNE}, n, ItemNeed.Necessity.REQUIRED,
			ItemNeed.SatisfiedBy.INVENTORY_OR_RUNE_POUCH, "spells");
	}

	private static ItemNeed air(int n)
	{
		return new ItemNeed("rune.air", "Air rune", "Travel", new int[]{ItemID.AIRRUNE}, n, ItemNeed.Necessity.REQUIRED,
			ItemNeed.SatisfiedBy.INVENTORY_OR_RUNE_POUCH, "spells");
	}

	private static NeedResult one(ItemNeed need, AccountCapability cap)
	{
		return PreparationEvaluator.evaluate(new PreparationRequest("t", "t", Collections.singletonList(need)), cap, NOW).getResults().get(0);
	}

	@Test
	public void wornCountsOnlyWhereAllowed()
	{
		ItemNeed cloak = new ItemNeed("travel.cloak", "Ardougne cloak", "Travel", new int[]{ItemID.ARDY_CAPE_MEDIUM, ItemID.ARDY_CAPE_ELITE}, 0,
			ItemNeed.Necessity.REQUIRED, ItemNeed.SatisfiedBy.INVENTORY_OR_WORN, "");
		assertEquals(NeedStatus.CARRIED, one(cloak, base(1).worn(items(ItemID.ARDY_CAPE_ELITE, 1)).build()).getStatus());
		assertEquals("worn", one(cloak, base(1).worn(items(ItemID.ARDY_CAPE_ELITE, 1)).build()).getDetail());
		ItemNeed tablet = new ItemNeed("travel.tab", "Varrock teleport", "Travel", new int[]{ItemID.POH_TABLET_VARROCKTELEPORT}, 1,
			ItemNeed.Necessity.REQUIRED, ItemNeed.SatisfiedBy.INVENTORY_ONLY, "");
		assertEquals(NeedStatus.UNKNOWN, one(tablet, base(1).worn(items(ItemID.POH_TABLET_VARROCKTELEPORT, 1)).build()).getStatus());
	}

	@Test
	public void runes_countPouchOnlyWhenCarried_staffAndCombinationRunes()
	{
		AccountCapability pouch = base(1).inventory(items(ItemID.BH_RUNE_POUCH, 1)).runePouch(items(ItemID.LAWRUNE, 10)).build();
		NeedResult r = one(law(4), pouch);
		assertEquals(NeedStatus.CARRIED, r.getStatus());
		assertEquals("pouch", r.getDetail());
		AccountCapability unread = base(1).inventory(items(ItemID.BH_RUNE_POUCH, 1)).build();
		assertEquals(NeedStatus.UNKNOWN, one(law(4), unread).getStatus());
		AccountCapability banked = base(1).runePouch(items(ItemID.LAWRUNE, 10)).bank(bank(HOUR, ItemID.DIVINE_RUNE_POUCH, 1)).build();
		assertEquals("the pouch's contents are where the pouch is", NeedStatus.IN_BANK, one(law(4), banked).getStatus());
		assertEquals(NeedStatus.CARRIED, one(air(5), base(1).worn(items(ItemID.STAFF_OF_AIR, 1)).build()).getStatus());
		assertEquals("staff", one(air(5), base(1).worn(items(ItemID.STAFF_OF_AIR, 1)).build()).getDetail());
		assertEquals(NeedStatus.CARRIED, one(air(5), base(1).inventory(items(ItemID.MISTRUNE, 5)).build()).getStatus());
		assertEquals("a staff in the bank is fetchable", NeedStatus.IN_BANK, one(air(5), base(1).bank(bank(HOUR, ItemID.AIR_BATTLESTAFF, 1)).build()).getStatus());
	}

	@Test
	public void noOwner_orNoInventory_isUnknown_neverNotFound()
	{
		assertEquals(NeedStatus.UNKNOWN, one(seeds(1), AccountCapability.none(AccountProfileGate.Status.NOT_LOGGED_IN, NOW)).getStatus());
		assertEquals(NeedStatus.UNKNOWN, one(seeds(1), base(1).inventory(null).bank(bank(HOUR)).build()).getStatus());
	}

	@Test
	public void bankTab_followsTheBankBuildLayout()
	{
		java.util.Map<Integer, Integer> slots = new java.util.HashMap<>();
		slots.put(1, 0);
		slots.put(2, 2);
		slots.put(3, 6);
		slots.put(4, 7);
		StorageEvidence e = new StorageEvidence(StorageEvidence.Kind.BANK, items(1, 1, 2, 1, 3, 1, 4, 1), NOW, slots, new int[]{2, 0, 5, 0, 0, 0, 0, 0, 0});
		assertEquals(1, e.tabOf(1));
		assertEquals("an empty tab 2 is skipped", 3, e.tabOf(2));
		assertEquals(3, e.tabOf(3));
		assertEquals("after the nine counts: the main tab", 0, e.tabOf(4));
		assertEquals(-1, new StorageEvidence(StorageEvidence.Kind.BANK, items(1, 1), NOW).tabOf(1));
	}

	@Test
	public void setOff_onlyWhenConfirmedAndTheBankCloses()
	{
		SetOffDetector d = new SetOffDetector();
		assertFalse(d.onTick(Readiness.MISSING, true));
		assertFalse("bank closed without being ready", d.onTick(Readiness.MISSING, false));
		assertFalse(d.onTick(Readiness.CONFIRMED, false));
		assertFalse("ready but the bank was never open", d.onTick(Readiness.CONFIRMED, false));
		assertFalse(d.onTick(Readiness.CONFIRMED, true));
		assertTrue(d.onTick(Readiness.CONFIRMED, false));
		assertFalse("only once", d.onTick(Readiness.CONFIRMED, false));
		assertFalse(d.onTick(Readiness.CONFIRMED, true));
		assertFalse(d.onTick(Readiness.MISSING, true));
		assertFalse("not ready any more when it closed", d.onTick(Readiness.MISSING, false));
	}

	@Test
	public void setOff_neverTreatsUnverifiedTravelAsReady()
	{
		SetOffDetector d = new SetOffDetector();
		assertFalse(d.onTick(Readiness.UNVERIFIED, true));
		assertFalse("supplies carried but charges unknown: closing the bank does not close the stage", d.onTick(Readiness.UNVERIFIED, false));
		assertFalse(d.onTick(Readiness.UNVERIFIED, true));
		assertFalse(d.onTick(Readiness.CONFIRMED, false));
	}

	@Test
	public void readiness_suppliesCarriedIsNotEnoughWhileSomethingIsUnverified()
	{
		PreparationState carried = PreparationEvaluator.evaluate(new PreparationRequest("t", "t", Collections.singletonList(seeds(6))),
			base(1).inventory(items(ItemID.RANARR_SEED, 6)).build(), NOW);
		assertEquals(Readiness.CONFIRMED, carried.readiness(0));
		assertEquals(Readiness.UNVERIFIED, carried.readiness(1));
		PreparationState missing = PreparationEvaluator.evaluate(new PreparationRequest("t", "t", Collections.singletonList(seeds(6))),
			base(1).inventory(items(ItemID.RANARR_SEED, 2)).build(), NOW);
		assertEquals(Readiness.MISSING, missing.readiness(0));
		assertEquals(Readiness.MISSING, missing.readiness(2));
	}

	@Test
	public void unobservableCharges_coverTheQuantity_butOnlyAsCarried()
	{
		ItemNeed talisman = new ItemNeed("travel.xeric", "Xeric's talisman", "Travel", new int[]{ItemID.XERIC_TALISMAN}, 2,
			ItemNeed.Necessity.REQUIRED, ItemNeed.SatisfiedBy.INVENTORY_ONLY, "two uses").withUnits(new int[]{ItemNeed.COVERS_ANY}, "charge");
		NeedResult r = one(talisman, base(1).inventory(items(ItemID.XERIC_TALISMAN, 1)).build());
		assertEquals("carrying it is all the evaluator can check; the consumer flags the charges as unverified", NeedStatus.CARRIED, r.getStatus());
	}

	@Test
	public void ownershipStaysApartFromCarried_andOldEvidenceKeepsItsAge()
	{
		StorageEvidence old = new StorageEvidence(StorageEvidence.Kind.BANK, items(ItemID.RANARR_SEED, 40), NOW - 30L * 24 * HOUR);
		NeedResult r = one(seeds(6), base(1).bank(old).build());
		assertEquals("a month-old bank view is still evidence", NeedStatus.IN_BANK, r.getStatus());
		assertFalse(r.isSatisfied());
		assertEquals(NOW - 30L * 24 * HOUR, r.getBankSeenAt());
		assertEquals(6, r.getWithdrawItems());
	}
}
