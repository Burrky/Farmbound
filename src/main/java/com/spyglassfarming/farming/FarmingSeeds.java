package com.spyglassfarming.farming;

import com.spyglassfarming.DataRows;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * What a planned crop needs, for crops with sourced data only: the seed or
 * sapling and how many per patch, the herb planting level (for "any herb
 * seed" plans), and the gardener's protection payment. A crop that is not
 * listed has no requirement Spyglass can state; the preparation says so and
 * never guesses an item.
 *
 * <h3>Provenance</h3>
 * The rows are in {@code farming/seeds.tsv}. Item ids: {@code net.runelite.api.gameval.ItemID}
 * (RuneLite 1.13.1; tests resolve each gameval name). Checked 2026-10-15 / 2026-10-16:
 * <ul>
 *   <li>Seeds per patch, OSRS Wiki: allotment 3; herb, flower, bush (Redberry
 *   seed: 1), seaweed spore 1; hops "×" column of the Seeds page (barley,
 *   hammerstone, asgarnian, yanillian, krandorian, wildblood 4; jute, flax,
 *   hemp, cotton 3). Trees, fruit trees, hardwood, calquat, celastrus,
 *   redwood, spirit and crystal trees are planted as one sapling.</li>
 *   <li>Herb planting levels, OSRS Wiki Seeds (herb table, Guam 9 to Torstol 85).</li>
 *   <li>Protection payments: Quest Helper {@code FarmingUtils} (Zoinkwiz/
 *   quest-helper master @ 5f79193, BSD-2) for tree, fruit tree, hardwood and
 *   calquat saplings, and the OSRS Wiki for allotments (Farming; Seeds; the
 *   Sweetcorn, Strawberry, Watermelon and Snape grass seed pages), hops,
 *   bushes and trees (Seeds). Quest Helper lists ironwood as
 *   {@code ItemID.CURRY}; the wiki (Ironwood seed) says curry leaves, which
 *   is used here. Payments may be noted (OSRS Wiki, Farming).</li>
 * </ul>
 * Goutweed (a tuber) and the scarecrow are not listed. Herbs and flowers
 * cannot be protected (OSRS Wiki, Farming).
 */
public final class FarmingSeeds
{
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Seed
	{
		private final int itemId;
		private final String gameval;
		@Getter
		private final int perPatch;
		/** A potted sapling: one per patch, and it does not stack in the inventory. */
		@Getter
		private final boolean sapling;

		public int getItemId()
		{
			return itemId;
		}

		public String getGameval()
		{
			return gameval;
		}
	}

	/** A gardener's protection payment. */
	public static final class Payment
	{
		private final int itemId;
		private final String gameval;
		/** "Basket of tomatoes", "Compost". */
		@Getter
		private final String label;
		@Getter
		private final int quantity;

		/** Further items the same payment needs (a spirit tree takes three different items). */
		private final List<Payment> also = new ArrayList<>();

		/** The further items paid together with this one (empty for single-item payments). */
		public List<Payment> getAlso()
		{
			return Collections.unmodifiableList(also);
		}

		Payment(int itemId, String gameval, String label, int quantity)
		{
			this.itemId = itemId;
			this.gameval = gameval;
			this.label = label;
			this.quantity = quantity;
		}

		public int getItemId()
		{
			return itemId;
		}

		public String getGameval()
		{
			return gameval;
		}
	}

	private static final Map<FarmingProduce, Seed> SEEDS = new EnumMap<>(FarmingProduce.class);
	private static final Map<FarmingProduce, Integer> HERB_LEVEL = new EnumMap<>(FarmingProduce.class);
	private static final Map<FarmingProduce, Payment> PAYMENTS = new EnumMap<>(FarmingProduce.class);

	/**
	 * Planting levels, OSRS Wiki "Seed Table" (checked 2026-10-21), plus the
	 * hardwood levels from the wiki's special-patches table. Flax, hemp and
	 * cotton (Sailing-era hops) are not in that table: their level is unknown,
	 * so they are never filtered out.
	 */
	private static final Map<FarmingProduce, Integer> LEVEL = new EnumMap<>(FarmingProduce.class);

	static final String FILE = "/com/spyglassfarming/farming/seeds.tsv";

	static
	{
		DataRows.read(FILE, r ->
		{
			FarmingProduce crop = r.e(FarmingProduce.class, 1);
			switch (r.op())
			{
				case "herb":
					seed(crop, r.cols(4, 4), 1, false);
					HERB_LEVEL.put(crop, r.i(3));
					break;
				case "seed":
					seed(crop, r.cols(4, 4), r.i(3), false);
					break;
				case "sapling":
					seed(crop, r.cols(3, 3), 1, true);
					break;
				case "pay":
					if (PAYMENTS.put(crop, payment(r)) != null)
					{
						throw new IllegalArgumentException("second payment for " + crop);
					}
					break;
				case "also":
					Payment main = PAYMENTS.get(crop);
					if (main == null)
					{
						throw new IllegalArgumentException("no payment for " + crop);
					}
					main.also.add(payment(r));
					break;
				case "level":
					if (LEVEL.put(crop, r.cols(3, 3).i(2)) != null)
					{
						throw new IllegalArgumentException("second level for " + crop);
					}
					break;
				default:
					throw r.unknownOp();
			}
		});
		LEVEL.putAll(HERB_LEVEL);
	}

	private static void seed(FarmingProduce crop, DataRows.Row r, int perPatch, boolean sapling)
	{
		if (SEEDS.put(crop, new Seed(r.id(2), r.gameval(2), perPatch, sapling)) != null)
		{
			throw new IllegalArgumentException("second seed for " + crop);
		}
	}

	private static Payment payment(DataRows.Row r)
	{
		return new Payment(r.cols(5, 5).id(2), r.gameval(2), r.s(3), r.i(4));
	}

	private FarmingSeeds()
	{
	}

	/** The crop's planting level, or null when it is not sourced (never guessed). */
	public static Integer level(FarmingProduce produce)
	{
		return produce == null ? null : LEVEL.get(produce);
	}

	/**
	 * Crops with seed data that a patch type takes and that {@code farmingLevel}
	 * can plant (all of them when the level is unknown; crops whose level is not
	 * sourced always), highest level first. Any of them satisfies the patch
	 * type's generic planting need: the player decides in game.
	 */
	public static List<FarmingProduce> plantable(FarmingPatchType type, Integer farmingLevel)
	{
		List<FarmingProduce> out = new ArrayList<>();
		for (Map.Entry<FarmingProduce, Seed> e : SEEDS.entrySet())
		{
			FarmingProduce p = e.getKey();
			if (p.getPatchType() != type)
			{
				continue;
			}
			Integer need = LEVEL.get(p);
			if (farmingLevel == null || need == null || need <= farmingLevel)
			{
				out.add(p);
			}
		}
		out.sort((a, b) -> Integer.compare(LEVEL.getOrDefault(b, 0), LEVEL.getOrDefault(a, 0)));
		return out;
	}

	/** Most seeds (or saplings) any listed crop of the type takes per patch (hops: 4, but 3 for jute and the Sailing hops). */
	public static int maxPerPatch(FarmingPatchType type)
	{
		int max = 0;
		for (Map.Entry<FarmingProduce, Seed> e : SEEDS.entrySet())
		{
			if (e.getKey().getPatchType() == type)
			{
				max = Math.max(max, e.getValue().getPerPatch());
			}
		}
		return max;
	}

	/** Fewest per patch for the type (equal to {@link #maxPerPatch} unless crops differ). */
	public static int minPerPatch(FarmingPatchType type)
	{
		int min = Integer.MAX_VALUE;
		for (Map.Entry<FarmingProduce, Seed> e : SEEDS.entrySet())
		{
			if (e.getKey().getPatchType() == type)
			{
				min = Math.min(min, e.getValue().getPerPatch());
			}
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	/** True when the type is planted with saplings (unstackable, one per patch). */
	public static boolean saplings(FarmingPatchType type)
	{
		for (Map.Entry<FarmingProduce, Seed> e : SEEDS.entrySet())
		{
			if (e.getKey().getPatchType() == type)
			{
				return e.getValue().isSapling();
			}
		}
		return false;
	}

	/** "Herb seeds", "Tree saplings", "Mushroom spores": the planting category for a patch type. */
	public static String category(FarmingPatchType type)
	{
		switch (type)
		{
			case HERB:
				return "Herb seeds";
			case ALLOTMENT:
				return "Allotment seeds";
			case FLOWER:
				return "Flower seeds";
			case HOPS:
				return "Hops seeds";
			case BUSH:
				return "Bush seeds";
			case TREE:
				return "Tree saplings";
			case FRUIT_TREE:
				return "Fruit tree saplings";
			case HARDWOOD_TREE:
				return "Hardwood saplings";
			case SEAWEED:
				return "Seaweed spores";
			case MUSHROOM:
				return "Mushroom spores";
			case CACTUS:
				return "Cactus seeds";
			case ANIMA:
				return "Anima seed";
			case HESPORI:
				return "Hespori seed";
			case BELLADONNA:
				return "Belladonna seeds";
			default:
				return type.getDisplayName() + " sapling";
		}
	}

	/** The crop a carried item plants, or null. */
	public static FarmingProduce cropOf(int itemId)
	{
		for (Map.Entry<FarmingProduce, Seed> e : SEEDS.entrySet())
		{
			if (e.getValue().getItemId() == itemId)
			{
				return e.getKey();
			}
		}
		return null;
	}

	/** The seed or sapling for a planned crop, or null when Spyglass has no sourced data for it. */
	public static Seed forCrop(FarmingProduce produce)
	{
		return produce == null ? null : SEEDS.get(produce);
	}

	/** The protection payment for a crop, or null (cannot be protected, or not sourced). */
	public static Payment paymentFor(FarmingProduce produce)
	{
		return produce == null ? null : PAYMENTS.get(produce);
	}

	public static Map<FarmingProduce, Seed> all()
	{
		return Collections.unmodifiableMap(SEEDS);
	}

}
