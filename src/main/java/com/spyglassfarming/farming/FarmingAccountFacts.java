package com.spyglassfarming.farming;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.EvidenceSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Account facts Farming reads natively, each owned by the account / profile
 * the capability belongs to, and each UNKNOWN until the capability says the
 * account's variables were evidenced as loaded (never zero, empty or
 * unavailable by default):
 * <ul>
 *   <li>patch access ({@link FarmingAccess});</li>
 *   <li>the tool leprechaun's stored tools and compost;</li>
 * </ul>
 * Pure: built from a capability snapshot and evidence; no client access.
 */
public final class FarmingAccountFacts
{
	/**
	 * The tool leprechaun's storage, decoded exactly as the game's own tool-store
	 * script displays it (client script 1063, cache 2026-10-14-rev241, read from
	 * abextm/osrs-cache): each tool is {@code base + 2 × extra}, compost and
	 * supercompost {@code base + 256 × extra}, buckets {@code base + 32 × extra +
	 * 256 × extra2}, ultracompost its own varbit; secateurs are shown as magic
	 * secateurs when {@code FARMING_TOOLS_FAIRYSECATEURS} is 1; the bottomless
	 * bucket is absent (0), empty (1) or filled (more). Shared by every tool
	 * leprechaun. Its charges are not decoded.
	 */
	public static final class ToolStore
	{
		@Getter
		private final Map<Integer, Integer> counts;
		@Getter
		private final boolean magicSecateurs;
		/** 0 none, 1 empty, 2+ filled (which compost is not decoded). */
		@Getter
		private final int bottomless;

		ToolStore(Map<Integer, Integer> counts, boolean magicSecateurs, int bottomless)
		{
			this.counts = Collections.unmodifiableMap(counts);
			this.magicSecateurs = magicSecateurs;
			this.bottomless = bottomless;
		}

		/** Stored count of an item (RAKE, DIBBER, SPADE, SECATEURS / magic secateurs, TROWEL, BUCKET_EMPTY, the three composts, plant cure). */
		public int count(int itemId)
		{
			return counts.getOrDefault(itemId, 0);
		}
	}

	@Getter
	private final FarmingAccess.Resolver access;
	/** The account's variables were evidenced as loaded (gated patches can be judged). */
	@Getter
	private final boolean accountRead;

	FarmingAccountFacts(FarmingAccess.Resolver access, boolean accountRead)
	{
		this.access = access;
		this.accountRead = accountRead;
	}

	/** No account facts (logged out, tests): every gated patch unverified, nothing else known. */
	public static final FarmingAccountFacts NONE = new FarmingAccountFacts(FarmingAccess.Resolver.NONE, false);

	/** Tests and fixtures that predate access gating: every patch open, nothing else known. */
	public static final FarmingAccountFacts ALL_OPEN = new FarmingAccountFacts(patchId -> FarmingAccess.Access.OPEN, true);

	public static FarmingAccountFacts of(AccountCapability cap, EvidenceSource evidence)
	{
		if (cap == null || cap.getOwner() == null)
		{
			return NONE;
		}
		return new FarmingAccountFacts(FarmingAccess.resolver(cap, evidence), cap.isSettled());
	}

	static ToolStore toolStore(AccountCapability cap)
	{
		int[] needed = {VarbitID.FARMING_TOOLS_RAKE, VarbitID.FARMING_TOOLS_EXTRARAKES, VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS,
			VarbitID.FARMING_TOOLS_SPADE, VarbitID.FARMING_TOOLS_EXTRASPADES, VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS,
			VarbitID.FARMING_TOOLS_FAIRYSECATEURS, VarbitID.FARMING_TOOLS_TROWEL, VarbitID.FARMING_TOOLS_EXTRATROWELS, VarbitID.FARMING_TOOLS_BUCKETS,
			VarbitID.FARMING_TOOLS_EXTRABUCKETS, VarbitID.FARMING_TOOLS_EXTRA2BUCKETS, VarbitID.FARMING_TOOLS_COMPOST, VarbitID.FARMING_TOOLS_EXTRACOMPOST,
			VarbitID.FARMING_TOOLS_SUPERCOMPOST, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST, VarbitID.FARMING_TOOLS_ULTRACOMPOST,
			VarbitID.FARMING_TOOLS_PLANTCURE, VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE};
		for (int v : needed)
		{
			if (cap.varbit(v) == null)
			{
				return null; // not read: unknown, never "empty"
			}
		}
		Map<Integer, Integer> c = new HashMap<>();
		c.put(ItemID.RAKE, v(cap, VarbitID.FARMING_TOOLS_RAKE) + 2 * v(cap, VarbitID.FARMING_TOOLS_EXTRARAKES));
		c.put(ItemID.DIBBER, v(cap, VarbitID.FARMING_TOOLS_DIBBER) + 2 * v(cap, VarbitID.FARMING_TOOLS_EXTRADIBBERS));
		c.put(ItemID.SPADE, v(cap, VarbitID.FARMING_TOOLS_SPADE) + 2 * v(cap, VarbitID.FARMING_TOOLS_EXTRASPADES));
		boolean magic = v(cap, VarbitID.FARMING_TOOLS_FAIRYSECATEURS) == 1;
		c.put(magic ? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS,
			v(cap, VarbitID.FARMING_TOOLS_SECATEURS) + 2 * v(cap, VarbitID.FARMING_TOOLS_EXTRASECATEURS));
		c.put(ItemID.TROWEL, v(cap, VarbitID.FARMING_TOOLS_TROWEL) + 2 * v(cap, VarbitID.FARMING_TOOLS_EXTRATROWELS));
		c.put(ItemID.BUCKET_EMPTY, v(cap, VarbitID.FARMING_TOOLS_BUCKETS) + 32 * v(cap, VarbitID.FARMING_TOOLS_EXTRABUCKETS)
			+ 256 * v(cap, VarbitID.FARMING_TOOLS_EXTRA2BUCKETS));
		c.put(ItemID.BUCKET_COMPOST, v(cap, VarbitID.FARMING_TOOLS_COMPOST) + 256 * v(cap, VarbitID.FARMING_TOOLS_EXTRACOMPOST));
		c.put(ItemID.BUCKET_SUPERCOMPOST, v(cap, VarbitID.FARMING_TOOLS_SUPERCOMPOST) + 256 * v(cap, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST));
		c.put(ItemID.BUCKET_ULTRACOMPOST, v(cap, VarbitID.FARMING_TOOLS_ULTRACOMPOST));
		c.put(ItemID.PLANT_CURE, v(cap, VarbitID.FARMING_TOOLS_PLANTCURE));
		return new ToolStore(c, magic, v(cap, VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE));
	}

	private static int v(AccountCapability cap, int varbit)
	{
		Integer x = cap.varbit(varbit);
		return x == null ? 0 : x;
	}

	/** Every varbit these facts read (registered with the account adapter). */
	public static int[] varbits()
	{
		return new int[]{VarbitID.FARMING_TOOLS_RAKE,
			VarbitID.FARMING_TOOLS_EXTRARAKES, VarbitID.FARMING_TOOLS_DIBBER, VarbitID.FARMING_TOOLS_EXTRADIBBERS, VarbitID.FARMING_TOOLS_SPADE,
			VarbitID.FARMING_TOOLS_EXTRASPADES, VarbitID.FARMING_TOOLS_SECATEURS, VarbitID.FARMING_TOOLS_EXTRASECATEURS,
			VarbitID.FARMING_TOOLS_FAIRYSECATEURS, VarbitID.FARMING_TOOLS_TROWEL, VarbitID.FARMING_TOOLS_EXTRATROWELS, VarbitID.FARMING_TOOLS_BUCKETS,
			VarbitID.FARMING_TOOLS_EXTRABUCKETS, VarbitID.FARMING_TOOLS_EXTRA2BUCKETS, VarbitID.FARMING_TOOLS_COMPOST,
			VarbitID.FARMING_TOOLS_EXTRACOMPOST, VarbitID.FARMING_TOOLS_SUPERCOMPOST, VarbitID.FARMING_TOOLS_EXTRASUPERCOMPOST,
			VarbitID.FARMING_TOOLS_ULTRACOMPOST, VarbitID.FARMING_TOOLS_PLANTCURE, VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE};
	}
}
