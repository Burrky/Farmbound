package com.spyglassfarming.farming;

import com.spyglassfarming.DataRows;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import net.runelite.api.gameval.VarbitID;

/**
 * The catalogued farming areas and how a location resolves to the patches
 * whose state the farming varbits currently carry.
 *
 * Area rows, alias ("extra") regions and bounds rules are derived from
 * RuneLite's FarmingWorld (master @ e98399f931c6, BSD 2-Clause, Copyright (c)
 * 2018 NotFoxtrot, Copyright (c) 2018 Abex; see THIRD_PARTY_NOTICES.md).
 *
 * COVERAGE: every RuneLite farming area, with every herb, allotment, flower,
 * tree, fruit tree, bush, hops and special patch (hardwood, redwood, spirit
 * tree, calquat, celastrus, cactus, seaweed, mushroom, belladonna, Hespori,
 * anima, crystal tree). NOT catalogued, by decision: compost bins (E/N), the
 * Hosidius vinery's grape vines, and the Great Conch coral nurseries (not
 * Spyglass run types). A patch that is not listed is simply never read; the
 * areas that share a map region (Falador / Port Sarim in 12083, Catherby /
 * its fruit tree in 11317) are separated by the bounds rules reproduced
 * below, so no varbit is ever attributed to the wrong patch.
 *
 * The areas and patches are rows of {@code farming/patches.tsv}; the bounds rules stay here.
 * Patch ids are persisted: an existing id is never renamed.
 */
public final class FarmingPatchCatalog
{
	/** The x / y / plane rule RuneLite's FarmingRegion.isInBounds applies. */
	interface Bounds
	{
		boolean contains(FarmingLocation location);
	}

	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class Area
	{
		final String name;
		final int owningRegionId;
		final int[] aliasRegionIds;
		final Bounds bounds;
		final List<FarmingPatchDef> patches;
	}

	private static final Bounds ANYWHERE = location -> true;

	/** Falador 12083: not the region's south edge, which carries the Port Sarim spirit tree instead. */
	static final Bounds FALADOR_ALLOTMENTS = location -> location.getY() >= 3272;

	/** Catherby 11062: in the north-east corner only the ground floor of the patch area transmits these (upstairs sends different varbits). */
	static final Bounds CATHERBY = location ->
	{
		if (location.getX() >= 2816 && location.getY() < 3456)
		{
			return location.getX() < 2840 && location.getY() >= 3440 && location.getPlane() == 0;
		}
		return true;
	};

	/** Port Sarim 12082 (and the south edge of 12083): the spirit tree, not Falador's allotments. */
	static final Bounds PORT_SARIM = location -> location.getY() < 3272;

	/** Catherby 11317: the fruit tree is sent everywhere the allotment box rule does not apply, and always upstairs. */
	static final Bounds CATHERBY_FRUIT_TREE = location -> location.getX() >= 2840 || location.getY() < 3440 || location.getPlane() == 1;

	/**
	 * Fossil Island: the hardwood trees are sent anywhere on plane 0, except on a
	 * few stair and ladder tiles where the varbits arrive a tick early.
	 */
	static final Bounds FOSSIL_ISLAND = location ->
	{
		int x = location.getX();
		int y = location.getY();
		if (x == 3753 && y >= 3868 && y <= 3870)
		{
			return false;
		}
		if ((x == 3729 || x == 3728 || x == 3747 || x == 3746) && y <= 3832 && y >= 3830)
		{
			return false;
		}
		return location.getPlane() == 0;
	};

	private static final FarmingPatchCatalog DEFAULT = new FarmingPatchCatalog(defaultAreas());

	private final List<Area> areas;
	private final Map<Integer, List<Area>> areasByRegion = new HashMap<>();
	private final Map<String, FarmingPatchDef> patchesById = new HashMap<>();

	FarmingPatchCatalog(List<Area> areas)
	{
		this.areas = Collections.unmodifiableList(new ArrayList<>(areas));
		for (Area area : areas)
		{
			areasByRegion.computeIfAbsent(area.owningRegionId, k -> new ArrayList<>()).add(area);
			for (int alias : area.aliasRegionIds)
			{
				List<Area> list = areasByRegion.computeIfAbsent(alias, k -> new ArrayList<>());
				if (!list.contains(area))
				{
					list.add(area); // RuneLite lists Great Conch's own region among its aliases too
				}
			}
			for (FarmingPatchDef patch : area.patches)
			{
				if (patchesById.put(patch.getPatchId(), patch) != null)
				{
					throw new IllegalArgumentException("Duplicate patchId " + patch.getPatchId());
				}
			}
		}
	}

	public static FarmingPatchCatalog standard()
	{
		return DEFAULT;
	}

	public FarmingPatchDef patch(String patchId)
	{
		return patchesById.get(patchId);
	}

	/** The owning region first, then the alias regions, of the area holding {@code patchId}; empty if unknown. */
	int[] regionsOf(String patchId)
	{
		for (Area area : areas)
		{
			for (FarmingPatchDef p : area.patches)
			{
				if (p.getPatchId().equals(patchId))
				{
					int[] out = new int[area.aliasRegionIds.length + 1];
					out[0] = area.owningRegionId;
					System.arraycopy(area.aliasRegionIds, 0, out, 1, area.aliasRegionIds.length);
					return out;
				}
			}
		}
		return new int[0];
	}

	public List<FarmingPatchDef> allPatches()
	{
		List<FarmingPatchDef> out = new ArrayList<>(patchesById.values());
		out.sort(Comparator.comparing(FarmingPatchDef::getPatchId));
		return out;
	}

	/** The patches the farming varbits describe at {@code location}; {@link TransmissionContext#NONE} when none. */
	public TransmissionContext resolve(FarmingLocation location)
	{
		if (location == null)
		{
			return TransmissionContext.NONE;
		}
		List<Area> candidates = areasByRegion.get(location.getRegionId());
		if (candidates == null)
		{
			return TransmissionContext.NONE;
		}
		List<FarmingPatchDef> patches = new ArrayList<>();
		for (Area area : candidates)
		{
			if (area.bounds.contains(location))
			{
				patches.addAll(area.patches);
			}
		}
		if (patches.isEmpty())
		{
			return TransmissionContext.NONE;
		}
		patches.sort(Comparator.comparing(FarmingPatchDef::getPatchId));
		return new TransmissionContext(patches);
	}

	static final String FILE = "/com/spyglassfarming/farming/patches.tsv";

	private static List<Area> defaultAreas()
	{
		List<Area> a = new ArrayList<>();
		DataRows.read(FILE, r ->
		{
			if (r.op().equals("area"))
			{
				String[] alias = r.cols(5, 5).s(4).isEmpty() ? new String[0] : r.s(4).split(",");
				int[] aliases = new int[alias.length];
				for (int i = 0; i < alias.length; i++)
				{
					aliases[i] = Integer.parseInt(alias[i]);
				}
				a.add(new Area(r.s(1), r.i(2), aliases, bounds(r.s(3)), new ArrayList<>()));
				return;
			}
			if (a.isEmpty())
			{
				throw new IllegalArgumentException("a patch before any area");
			}
			Area area = a.get(a.size() - 1);
			switch (r.op())
			{
				case "patch":
					area.patches.add(new FarmingPatchDef(r.cols(5, 5).s(1), area.name, r.s(2), r.e(FarmingPatchType.class, 3), area.owningRegionId,
						r.id(4)));
					break;
				case "allotments":
					String slug = r.cols(6, 6).s(1);
					patch(area, "allotment." + slug + "." + r.s(2), r.s(3), FarmingPatchType.ALLOTMENT, VarbitID.FARMING_TRANSMIT_A);
					patch(area, "allotment." + slug + "." + r.s(4), r.s(5), FarmingPatchType.ALLOTMENT, VarbitID.FARMING_TRANSMIT_B);
					patch(area, "flower." + slug, "", FarmingPatchType.FLOWER, VarbitID.FARMING_TRANSMIT_C);
					patch(area, "herb." + slug, "", FarmingPatchType.HERB, VarbitID.FARMING_TRANSMIT_D);
					break;
				default:
					throw r.unknownOp();
			}
		});
		return a;
	}

	private static void patch(Area area, String id, String label, FarmingPatchType type, int varbit)
	{
		area.patches.add(new FarmingPatchDef(id, area.name, label, type, area.owningRegionId, varbit));
	}

	private static Bounds bounds(String name)
	{
		switch (name)
		{
			case "ANYWHERE":
				return ANYWHERE;
			case "FALADOR_ALLOTMENTS":
				return FALADOR_ALLOTMENTS;
			case "CATHERBY":
				return CATHERBY;
			case "PORT_SARIM":
				return PORT_SARIM;
			case "CATHERBY_FRUIT_TREE":
				return CATHERBY_FRUIT_TREE;
			case "FOSSIL_ISLAND":
				return FOSSIL_ISLAND;
			default:
				throw new IllegalArgumentException("unknown bounds " + name);
		}
	}
}
