package com.spyglassfarming.farming;

import com.spyglassfarming.storage.SavedEnum;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * What the preview hands to Start Run: the selected run type (or types, for a
 * combined run), the included and excluded patches (deduplicated, in preview
 * order), the plan per included patch and the run options per type. Built
 * from the session-only the planning draft; creating one starts nothing.
 *
 * <p>A plan is a name: a specific crop ({@link FarmingProduce#name()}, the
 * player's explicit choice), {@link FarmingRunIntent#REPLANT_ANY} ("plant
 * something suitable; I'll decide") or {@link FarmingRunIntent#HARVEST_ONLY}.
 */
public final class FarmingRunRequest
{
	@Getter
	private final List<FarmingRunType> runTypes;
	@Getter
	private final List<String> included;
	@Getter
	private final List<String> excluded;
	private final Map<String, String> plans;
	private final Map<FarmingRunType, FarmingCompost> compost;
	private final Map<FarmingRunType, Boolean> protection;
	@Getter
	private final boolean useBounty;
	/** Optional work taken on at Start from the Farming settings (§28.16); null: none recorded (the preferences decide). */
	@Getter
	private List<String> bins;
	@Getter
	private Map<String, String> binUpgrades;
	@Getter
	private List<String> protectTypes;
	/**
	 * Pass 31: the player's saved compost per patch type for the run's patch types (null: none given).
	 * <p>
	 * The compost per patch type for the run (null: the request carries none, as before pass 31).
	 */
	@Getter
	private Map<String, String> compostByPatch;
	/** Pass 31: each run type's compost from the saved choices (its own patches'), over the draft's. */
	private final Map<FarmingRunType, FarmingCompost> compostFromPrefs = new EnumMap<>(FarmingRunType.class);

	/** One run type; {@code planned} null = harvest only (older callers and tests). */
	public FarmingRunRequest(FarmingRunType runType, List<String> included, List<String> excluded, Map<String, FarmingProduce> planned,
		FarmingCompost compost, boolean payProtection)
	{
		this(Collections.singletonList(runType), included, excluded, names(included, planned), single(runType, compost),
			single(runType, payProtection), false);
	}

	public FarmingRunRequest(List<FarmingRunType> runTypes, List<String> included, List<String> excluded, Map<String, String> plans,
		Map<FarmingRunType, FarmingCompost> compost, Map<FarmingRunType, Boolean> protection, boolean useBounty)
	{
		this.runTypes = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(runTypes)));
		Set<String> in = new LinkedHashSet<>(included);
		this.included = Collections.unmodifiableList(new ArrayList<>(in));
		List<String> out = new ArrayList<>();
		for (String id : excluded)
		{
			if (!in.contains(id) && !out.contains(id))
			{
				out.add(id);
			}
		}
		this.excluded = Collections.unmodifiableList(out);
		this.plans = Collections.unmodifiableMap(new LinkedHashMap<>(plans));
		this.compost = Collections.unmodifiableMap(compost == null ? new EnumMap<>(FarmingRunType.class) : new EnumMap<>(nonEmpty(compost)));
		this.protection = Collections.unmodifiableMap(protection == null ? new EnumMap<>(FarmingRunType.class) : new EnumMap<>(nonEmptyB(protection)));
		this.useBounty = useBounty;
	}

	private static Map<String, String> names(List<String> included, Map<String, FarmingProduce> planned)
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (String id : included)
		{
			FarmingProduce p = planned.get(id);
			out.put(id, p == null ? FarmingRunIntent.HARVEST_ONLY : p.name());
		}
		return out;
	}

	private static <V> Map<FarmingRunType, V> single(FarmingRunType type, V value)
	{
		Map<FarmingRunType, V> m = new LinkedHashMap<>();
		if (type != null && value != null)
		{
			m.put(type, value);
		}
		return m;
	}

	private static Map<FarmingRunType, FarmingCompost> nonEmpty(Map<FarmingRunType, FarmingCompost> m)
	{
		Map<FarmingRunType, FarmingCompost> out = new LinkedHashMap<>();
		m.forEach((k, v) -> out.put(k, v == null ? FarmingCompost.NONE : v));
		return out.isEmpty() ? new EnumMap<>(FarmingRunType.class) : out;
	}

	private static Map<FarmingRunType, Boolean> nonEmptyB(Map<FarmingRunType, Boolean> m)
	{
		Map<FarmingRunType, Boolean> out = new LinkedHashMap<>(m);
		return out.isEmpty() ? new EnumMap<>(FarmingRunType.class) : out;
	}

	/** The first run type (a combined run's id and fallbacks use it). */
	/** The request the preview shows right now: every visible row of the type(s), inclusion and plans from the draft. */
	public static FarmingRunRequest from(FarmingModel model, FarmingRunType type, FarmingPlanDraft draft)
	{
		return from(model, Collections.singletonList(type), draft);
	}

	/** A combined request: one run over several types, overlapping patches counted once. */
	public static FarmingRunRequest from(FarmingModel model, List<FarmingRunType> types, FarmingPlanDraft draft)
	{
		List<String> included = new ArrayList<>();
		List<String> excluded = new ArrayList<>();
		Map<String, String> plans = new LinkedHashMap<>();
		Map<FarmingRunType, FarmingCompost> compost = new LinkedHashMap<>();
		Map<FarmingRunType, Boolean> protection = new LinkedHashMap<>();
		boolean bounty = false;
		for (FarmingRunType type : types)
		{
			if (!type.isCrop())
			{
				continue; // the compost bin component: its bins come from the preferences (withExtras)
			}
			for (FarmingModel.Row r : model.rows(type))
			{
				if (included.contains(r.getPatchId()) || excluded.contains(r.getPatchId()))
				{
					continue;
				}
				if (draft.isIncluded(r, model.getNow()))
				{
					included.add(r.getPatchId());
					plans.put(r.getPatchId(), draft.plan(r));
				}
				else
				{
					excluded.add(r.getPatchId());
				}
			}
			compost.put(type, draft.getCompost(type));
			protection.put(type, draft.isProtectionPaid(type));
			bounty |= draft.isUseBounty() && FarmingPreparation.bountyApplies(type);
		}
		return new FarmingRunRequest(types, included, excluded, plans, compost, protection, bounty);
	}

	public FarmingRunType getRunType()
	{
		return runTypes.isEmpty() ? null : runTypes.get(0);
	}

	/** The plan name for a patch (a crop, REPLANT_ANY or HARVEST_ONLY); harvest only when absent. */
	public String plan(String patchId)
	{
		String p = plans.get(patchId);
		return p == null ? FarmingRunIntent.HARVEST_ONLY : p;
	}

	/** The explicit crop, or null (harvest only or "anything suitable"). */
	public FarmingProduce planned(String patchId)
	{
		String p = plan(patchId);
		return FarmingRunIntent.HARVEST_ONLY.equals(p) || FarmingRunIntent.REPLANT_ANY.equals(p) ? null : SavedEnum.parse(FarmingProduce.class, p);
	}

	/** The first type's compost (older single-type callers). */
	public FarmingCompost getCompost()
	{
		return compostFor(getRunType());
	}

	public FarmingCompost compostFor(FarmingRunType type)
	{
		FarmingCompost saved = type == null ? null : compostFromPrefs.get(type);
		if (saved != null)
		{
			return saved;
		}
		FarmingCompost c = type == null ? null : compost.get(type);
		return c == null ? FarmingCompost.NONE : c;
	}

	public boolean isPayProtection()
	{
		return protectionFor(getRunType());
	}

	public boolean protectionFor(FarmingRunType type)
	{
		return type != null && type.offersProtection() && Boolean.TRUE.equals(protection.get(type));
	}

	/** This request with the settings' optional work: the bins chosen for runs (and their upgrades) and the protected types. */
	public FarmingRunRequest withExtras(FarmingPreferences prefs)
	{
		return withExtras(prefs, runTypes.contains(FarmingRunType.COMPOST));
	}

	/**
	 * As above; the bins join only when the Compost bins component is part of the run ({@code includeBins}, §28.17): the
	 * chosen bins are a preference for that component, never a reason to add bins to a crop run.
	 */
	public FarmingRunRequest withExtras(FarmingPreferences prefs, boolean includeBins)
	{
		if (prefs == null)
		{
			return this;
		}
		bins = includeBins ? new ArrayList<>(prefs.getRunBins()) : new ArrayList<>();
		binUpgrades = new LinkedHashMap<>();
		for (String b : bins)
		{
			binUpgrades.put(b, prefs.binUpgrade(b).name());
		}
		protectTypes = new ArrayList<>();
		for (FarmingRunType t : FarmingRunType.values())
		{
			if (prefs.isProtect(t))
			{
				protectTypes.add(t.name());
			}
		}
		// The compost the player chose per patch type (one model for the overview's editor and run setup): what the run
		// prepares and guides. The per-run-type value follows the type's own patches, for older readers.
		if (!prefs.isCompostChosen())
		{
			return this; // never chosen on this profile: the request's own per-type choice stands
		}
		compostByPatch = new LinkedHashMap<>();
		for (FarmingRunType t : runTypes)
		{
			if (!t.isCrop())
			{
				continue;
			}
			compostByPatch.put(t.getPrimary().name(), prefs.compostFor(t.getPrimary()).name());
			for (FarmingPatchType c : t.getCompanions())
			{
				compostByPatch.put(c.name(), prefs.compostFor(c).name());
			}
			compostFromPrefs.put(t, prefs.compostFor(t.getPrimary()));
		}
		return this;
	}

	/**
	 * Problems that stop this request from starting, in plain words (empty =
	 * startable). Never-seen patches are fine: NOT TRACKED is not unavailable.
	 */
	public List<String> problems(FarmingPatchCatalog catalog)
	{
		List<String> out = new ArrayList<>();
		if (runTypes.isEmpty())
		{
			out.add("Pick a run type first.");
			return out;
		}
		boolean binsAsked = runTypes.contains(FarmingRunType.COMPOST);
		if (binsAsked && (bins == null || bins.isEmpty()))
		{
			out.add("Tick at least one compost bin to include.");
			return out;
		}
		if (included.isEmpty() && !binsAsked)
		{
			out.add("Include at least one patch to start.");
			return out;
		}
		for (String id : included)
		{
			FarmingPatchDef def = catalog.patch(id);
			boolean covered = false;
			for (FarmingRunType t : runTypes)
			{
				covered |= def != null && t.covers(def.getType());
			}
			if (!covered)
			{
				out.add("A selected patch isn't part of this run. Reopen the preview and try again.");
				return out;
			}
			FarmingProduce p = planned(id);
			if (p != null && p.getPatchType() != def.getType())
			{
				out.add(p.getDisplayName() + " can't be planted in the " + def.getAreaName() + " " + def.getType().getDisplayName().toLowerCase()
					+ " patch. Choose another planned crop.");
			}
		}
		return out;
	}
}
