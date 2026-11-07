package com.spyglassfarming.farming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The immutable presentation model of Farming: every catalogued patch's
 * {@link PatchStatus}, a per-run-type summary for the overview and the run-type
 * buttons, preview rows, and the Home card summary. Built from a
 * {@link FarmingSnapshot} by {@link #build}; pure, so Home and the Farming page
 * never decode, project or count anything themselves.
 */
public final class FarmingModel
{
	/** Semantic text tones; the UI maps them to theme colours. */
	public enum Tone
	{
		GOLD,
		EMBER,
		PRIMARY,
		SECONDARY,
		MUTED,
		/** Done (a run patch observed complete). */
		SAGE,
		/** Ready (pass 32: Home's Farming card, as the overview grid). */
		GREEN,
		/** Dead (pass 32: Home's Farming card, as the overview grid). */
		RED
	}

	/** One preview row (canonical §3.2, fixed three-line shape). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Row
	{
		@Getter
		private final FarmingPatchDef patch;
		@Getter
		private final PatchStatus status;
		private final String location;
		private final String crop;
		private final String chip;
		@Getter
		private final Tone chipTone;
		@Getter
		private final String provenance;
		/** A companion row (a flower patch in the Allotment preview). */
		@Getter
		private final boolean companion;

		public String getPatchId()
		{
			return patch.getPatchId();
		}

		/** "Falador (NW)", "Falador flower". */
		public String getLocation()
		{
			return location;
		}

		/** "Ranarr", "Unknown herb", "–". */
		public String getCrop()
		{
			return crop;
		}

		/** "READY", "~12m", "DISEASED", "CLEAR", "NOT TRACKED", ... */
		public String getChip()
		{
			return chip;
		}
	}

	/** A patch the browser does not offer, and why ("Locked: needs Farming 65", "Not confirmed yet: …"). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Excluded
	{
		@Getter
		private final Row row;
		@Getter
		private final FarmingAccess.Access access;

		/** "Needs Morytania elite diary" / "Not confirmed yet: quest state not read yet". */
		public String getReason()
		{
			String why = String.join("; ", access.getReasons());
			return access.getState() == FarmingAccess.Access.State.LOCKED ? "Needs " + why : "Not confirmed yet" + (why.isEmpty() ? "" : ": " + why);
		}
	}

	/** One run type's counts. */
	public static final class TypeSummary
	{
		@Getter
		int total;
		int tracked;
		@Getter
		int ready;
		/** Observed ready (harvestable / check health). */
		@Getter
		int readySeen;
		/** Estimated ready: the latest possible growth time has passed, but not seen ready. */
		@Getter
		int readyEstimated;
		int growing;
		int attention;
		int dead;
		@Getter
		int clear;
		int grown;
		/** Couldn't be read when last seen ("check patch"). */
		@Getter
		int check;
		/** The soonest estimated ready time among growing patches, or null. */
		@Getter
		Long soonestReadyAt;

		public int getTracked()
		{
			return tracked;
		}

		public int getDead()
		{
			return dead;
		}

		public int getGrowing()
		{
			return growing;
		}

		public int getAttention()
		{
			return attention;
		}
	}

	@Getter
	private final FarmingSnapshot.State state;
	@Getter
	private final FarmingOwner owner;
	@Getter
	private final long now;
	@Getter
	private final FarmingCalibration calibration;
	private final Map<String, PatchStatus> statuses;
	private final Map<FarmingRunType, TypeSummary> summaries;
	private final Map<FarmingRunType, List<Row>> rows;
	/** The owner's run (open, or complete and awaiting Done), or null. */
	@Getter
	private final FarmingRunView run;
	private Map<FarmingRunType, List<Excluded>> excluded = Collections.emptyMap();
	@Getter
	private FarmingAccountFacts facts = FarmingAccountFacts.ALL_OPEN;

	private FarmingModel(FarmingSnapshot.State state, FarmingOwner owner, long now, FarmingCalibration calibration, Map<String, PatchStatus> statuses,
		Map<FarmingRunType, TypeSummary> summaries, Map<FarmingRunType, List<Row>> rows, FarmingRunView run)
	{
		this.run = run;
		this.state = state;
		this.owner = owner;
		this.now = now;
		this.calibration = calibration;
		this.statuses = statuses;
		this.summaries = summaries;
		this.rows = rows;
	}

	/** Every patch offered (tests and fixtures without account facts). */
	public static FarmingModel build(FarmingPatchCatalog catalog, FarmingSnapshot snapshot, long now)
	{
		return build(catalog, snapshot, now, FarmingAccountFacts.ALL_OPEN);
	}

	/**
	 * The model with the access rule applied: a gated patch is offered (rows,
	 * summaries, overview, Home, run selection) only when its access is
	 * confirmed; locked and unverified patches are listed apart with the
	 * reason. Saved runs and every patch's knowledge are untouched.
	 */
	public static FarmingModel build(FarmingPatchCatalog catalog, FarmingSnapshot snapshot, long now, FarmingAccountFacts facts)
	{
		FarmingAccountFacts f = facts == null ? FarmingAccountFacts.NONE : facts;
		Map<FarmingRunType, List<Excluded>> excluded = new EnumMap<>(FarmingRunType.class);
		boolean available = snapshot.getState() == FarmingSnapshot.State.AVAILABLE;
		FarmingCalibration calibration = available ? FarmingCalibration.from(snapshot.getSamples()) : FarmingCalibration.NONE;
		Map<String, PatchStatus> statuses = new LinkedHashMap<>();
		Map<FarmingRunType, TypeSummary> summaries = new EnumMap<>(FarmingRunType.class);
		Map<FarmingRunType, List<Row>> rows = new EnumMap<>(FarmingRunType.class);
		for (FarmingRunType r : FarmingRunType.values())
		{
			summaries.put(r, new TypeSummary());
			rows.put(r, new ArrayList<>());
		}
		List<FarmingPatchDef> patches = new ArrayList<>(catalog.allPatches());
		patches.sort(Comparator.comparing(FarmingPatchDef::getAreaName).thenComparing(p -> p.getType().ordinal()).thenComparing(FarmingPatchDef::getLabel));
		for (FarmingPatchDef p : patches)
		{
			PatchKnowledge k = available ? snapshot.getKnowledge().get(p.getPatchId()) : null;
			PatchStatus s = FarmingProjection.project(p, k, calibration, now);
			statuses.put(p.getPatchId(), s);
			FarmingRunType runType = FarmingRunType.of(p.getType());
			boolean companion = p.getType() != runType.getPrimary();
			FarmingAccess.Access access = f.getAccess().access(p.getPatchId());
			if (!access.isOpen())
			{
				excluded.computeIfAbsent(runType, x -> new ArrayList<>()).add(new Excluded(row(p, s, companion, now), access));
				continue;
			}
			rows.get(runType).add(row(p, s, companion, now));
			count(summaries.get(runType), s); // flowers count in "Allotments & Flowers" too
		}
		for (List<Row> list : rows.values())
		{
			// Observed patches first (by site), then the never-seen ones: a player's real patches lead.
			list.sort(Comparator.comparing((Row r) -> r.isCompanion()).thenComparing(r -> !r.getStatus().isTracked()));
		}
		Map<FarmingRunType, List<Row>> frozen = new EnumMap<>(FarmingRunType.class);
		Map<String, Row> byId = new LinkedHashMap<>();
		for (Map.Entry<FarmingRunType, List<Row>> e : rows.entrySet())
		{
			frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
			for (Row r : e.getValue())
			{
				byId.put(r.getPatchId(), r);
			}
		}
		for (List<Excluded> list : excluded.values())
		{
			for (Excluded e : list)
			{
				byId.putIfAbsent(e.getRow().getPatchId(), e.getRow()); // a saved run may still hold it: it keeps its line
			}
		}
		FarmingRunView run = available && snapshot.getRun() != null
			? FarmingRunView.build(snapshot.getRun(), catalog, snapshot, byId, now, f.getAccess(), f.isAccountRead()) : null;
		Map<String, PatchStatus> offered = new LinkedHashMap<>();
		for (List<Row> list : frozen.values())
		{
			for (Row r : list)
			{
				offered.put(r.getPatchId(), r.getStatus());
			}
		}
		FarmingModel m = new FarmingModel(snapshot.getState(), snapshot.getOwner(), now, calibration, Collections.unmodifiableMap(statuses),
			Collections.unmodifiableMap(summaries), Collections.unmodifiableMap(frozen), run);
		m.excluded = Collections.unmodifiableMap(excluded);
		m.facts = f;
		m.bins = snapshot.getBins();
		return m;
	}


	/** The compost bins as last seen, by bin id (a bin never seen is absent). */
	@Getter
	private Map<String, CompostBin.Seen> bins = Collections.emptyMap();

	private static void count(TypeSummary t, PatchStatus s)
	{
		t.total++;
		if (!s.isTracked())
		{
			return;
		}
		t.tracked++;
		switch (s.getDisplay())
		{
			case READY:
				t.ready++;
				if (s.getReadySource() == PatchStatus.ReadySource.ESTIMATED)
				{
					t.readyEstimated++;
				}
				else
				{
					t.readySeen++;
				}
				break;
			case GROWING_TIMED:
				t.growing++;
				if (t.soonestReadyAt == null || s.getReadyAt() < t.soonestReadyAt)
				{
					t.soonestReadyAt = s.getReadyAt();
				}
				break;
			case GROWING:
				t.growing++;
				break;
			case DISEASED:
				t.attention++;
				break;
			case DEAD:
				t.attention++;
				t.dead++;
				break;
			case CLEAR:
				t.clear++;
				break;
			case GROWN:
				t.grown++;
				break;
			case CHECK_PATCH:
				t.check++;
				break;
			default:
				break;
		}
	}

	// -- rows ----------------------------------------------------------------------

	static Row row(FarmingPatchDef p, PatchStatus s, boolean companion, long now)
	{
		String location = location(p);
		String crop;
		if (s.getProduce() != null)
		{
			crop = s.getProduce().getDisplayName();
		}
		else if (s.isProduceUnknown())
		{
			crop = "Unknown " + p.getType().getDisplayName().toLowerCase();
		}
		else
		{
			crop = "–";
		}
		String chip;
		Tone tone;
		switch (s.getDisplay())
		{
			case READY:
				chip = "READY";
				tone = Tone.GOLD;
				break;
			case GROWING_TIMED:
				chip = FarmingText.remaining(s.getReadyAt() - now);
				tone = Tone.PRIMARY;
				break;
			case GROWING:
				chip = "GROWING";
				tone = Tone.SECONDARY;
				break;
			case GROWN:
				chip = "GROWN";
				tone = Tone.SECONDARY;
				break;
			case DISEASED:
				chip = "DISEASED";
				tone = Tone.EMBER;
				break;
			case DEAD:
				chip = "DEAD";
				tone = Tone.EMBER;
				break;
			case CLEAR:
				chip = "CLEAR";
				tone = Tone.SECONDARY;
				break;
			case CHECK_PATCH:
				chip = "CHECK PATCH?";
				tone = Tone.MUTED;
				break;
			case NOT_TRACKED:
			default:
				chip = "NOT TRACKED";
				tone = Tone.MUTED;
				break;
		}
		return new Row(p, s, location, crop, chip, tone, FarmingText.provenance(s, now), companion);
	}

	/** "Falador (NW)", "Fossil Island (East)", "Falador flower". */
	static String location(FarmingPatchDef p)
	{
		if (p.getType() == FarmingPatchType.FLOWER)
		{
			return p.getAreaName() + " flower";
		}
		String label = shortLabel(p.getLabel());
		return label.isEmpty() ? p.getAreaName() : p.getAreaName() + " (" + label + ")";
	}

	private static String shortLabel(String label)
	{
		switch (label)
		{
			case "North":
				return "N";
			case "South":
				return "S";
			case "North West":
				return "NW";
			case "South East":
				return "SE";
			case "North East":
				return "NE";
			case "South West":
				return "SW";
			default:
				return label;
		}
	}

	// -- overview --------------------------------------------------------------------

	// -- home ------------------------------------------------------------------------

	/** "herb" / "herbs", "fruit tree" / "fruit trees", "cactus" / "cacti". */
	static String noun(FarmingPatchType type, int count)
	{
		String one = type.getDisplayName().toLowerCase();
		if (count == 1)
		{
			return one;
		}
		switch (type)
		{
			case HOPS:
			case SEAWEED:
			case BELLADONNA:
			case CELASTRUS:
			case HESPORI:
				return one;
			case BUSH:
				return "bushes";
			case CACTUS:
				return "cacti";
			case ANIMA:
				return "anima plants";
			default:
				return one + "s";
		}
	}

	// -- accessors -------------------------------------------------------------------

	public PatchStatus status(String patchId)
	{
		return statuses.get(patchId);
	}

	public TypeSummary summary(FarmingRunType type)
	{
		return summaries.get(type);
	}

	/** The preview rows of a run type: its patches by site, companions (flowers) included. */
	public List<Row> rows(FarmingRunType type)
	{
		return rows.get(type);
	}

	/** Patches of a run type not offered (locked or not confirmed), with the reason. */
	public List<Excluded> excluded(FarmingRunType type)
	{
		return excluded.getOrDefault(type, Collections.emptyList());
	}
}
