package com.spyglassfarming.account.poh;

import com.spyglassfarming.DataRows;
import com.spyglassfarming.account.EvidenceSource;
import com.spyglassfarming.account.Known;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * What Spyglass has observed of the player's own house (canonical §13,
 * phase G). Facilities are read from the objects in the house scene; a visit
 * counts only when it is confirmed to be the player's own house (building
 * mode seen on during the visit, which is only possible at home), because a
 * friend's house has the same objects. Construction level, owning a house or
 * carrying a house teleport proves nothing.
 *
 * <ul>
 *   <li>Never confirmed: every facility UNKNOWN; POH routes are left out of
 *   planning (not "unavailable").</li>
 *   <li>Confirmed: the facilities seen are YES; anything not seen is NO (the
 *   whole house is in the loaded scene); a later confirmed visit replaces
 *   the set, so something removed disappears.</li>
 *   <li>Portal-nexus destinations are not readable from the object; they are
 *   read from the nexus's own menus (Teleport Menu, or the configuration's
 *   slots) when the player opens one during the visit. Until then a house
 *   destination the nexus might hold is unknown, never assumed.</li>
 * </ul>
 * Evidence keys: {@code poh.jewellery_box.basic|fancy|ornate},
 * {@code poh.mounted.xerics}, {@code poh.mounted.digsite},
 * {@code poh.portal.<destination>}, {@code poh.nexus}, {@code poh.fairy_ring},
 * {@code poh.spirit_tree}. Object ids: gameval ObjectID (RuneLite 1.13.1), in {@code account/poh/facilities.tsv}.
 */
public final class PohKnowledge
{
	static final Map<Integer, String> OBJECTS = new LinkedHashMap<>();

	/**
	 * Portal nexus destinations by the name the nexus menus show (OSRS Wiki "Portal nexus" / "Portal chamber" destination
	 * names; matched case-insensitively within a menu row). Keys are the same destinations as the portals'.
	 */
	static final Map<String, String> NEXUS_NAMES = new LinkedHashMap<>();

	static
	{
		DataRows.read("/com/spyglassfarming/account/poh/facilities.tsv", r ->
		{
			switch (r.op())
			{
				case "object":
					for (int id : r.ids(2))
					{
						object(id, r.s(1));
					}
					break;
				case "portal":
					portal(r.s(1), r.ids(2));
					break;
				case "portal_pair":
					portalPair(r.s(1), r.s(2), r.ids(3));
					break;
				case "nexus":
					nexus(r.s(1), r.rest(2));
					break;
				default:
					throw r.unknownOp();
			}
		});
	}

	private static void nexus(String dest, String... names)
	{
		for (String n : names)
		{
			NEXUS_NAMES.put(normal(n), dest);
		}
	}

	static String normal(String s)
	{
		return s.toLowerCase(Locale.ROOT).replaceAll("<[^>]*>", " ").replaceAll("[^a-z' ]", " ").replace("'", "").replaceAll("\\s+", " ").trim();
	}

	/**
	 * The nexus destination a menu row names, or null. Rows read like "<key>: <destination>"; the longest destination name
	 * contained in the row wins (so "West Ardougne" is never read as "Ardougne", nor "Ape Atoll Dungeon" as "Marim").
	 */
	public static String nexusDestination(String rowText)
	{
		if (rowText == null)
		{
			return null;
		}
		String row = " " + normal(rowText) + " ";
		String best = null;
		int bestLen = 0;
		for (Map.Entry<String, String> e : NEXUS_NAMES.entrySet())
		{
			if (e.getKey().length() > bestLen && row.contains(" " + e.getKey() + " "))
			{
				best = e.getValue();
				bestLen = e.getKey().length();
			}
		}
		return best;
	}

	/** A house destination (portal or nexus): {@code poh.dest.<destination>}. */
	public static final String DEST = "poh.dest.";

	/**
	 * Recorded when the portal nexus's Teleport Menu (the list it teleports with, in slot order) was read during the visit.
	 * Version 2 (2026-10-22): only that menu counts. The configuration screen is not read: a destination dragged into its
	 * Slots list is not installed until the change is confirmed and paid for.
	 */
	public static final String NEXUS_READ = "poh.nexus.menu";

	/** The first collector's marker: it also read the configuration screen, so its nexus destinations are not trusted. */
	static final String NEXUS_READ_V1 = "poh.nexus.read";

	private static void object(int id, String facilities)
	{
		if (OBJECTS.put(id, facilities) != null)
		{
			throw new IllegalArgumentException("duplicate object " + id);
		}
	}

	private static void portal(String dest, int... ids)
	{
		for (int id : ids)
		{
			object(id, "poh.portal." + dest);
		}
	}

	/** A diary form of a portal: {@code left} on left-click, {@code right} as its right-click option. */
	private static void portalPair(String left, String right, int... ids)
	{
		for (int id : ids)
		{
			object(id, "poh.portal." + left + "+poh.portal_alt." + right);
		}
	}

	/** A portal-chamber, mount or ring fact (not a nexus slot): what identifies the house's layout. */
	static boolean structural(String key)
	{
		return !key.startsWith("poh.nexus.");
	}

	/** Saved form (RuneLite profile config); field names are the JSON. */
	private Set<String> facilities = new LinkedHashSet<>();
	@Getter
	private long confirmedAt;
	private Set<String> unconfirmed = new LinkedHashSet<>();
	private long unconfirmedAt;

	public static PohKnowledge empty()
	{
		return new PohKnowledge();
	}

	/** The object ids that are this facility ("poh.portal.weiss", "poh.portal_alt.grand_exchange", "poh.nexus"), for highlighting. */
	public static Set<Integer> objectIds(String facility)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (Map.Entry<Integer, String> e : OBJECTS.entrySet())
		{
			for (String f : e.getValue().split("\\+"))
			{
				if (f.equals(facility))
				{
					out.add(e.getKey());
				}
			}
		}
		return out;
	}

	/** The facility keys an object id stands for (empty for anything else). */
	public static Set<String> facilitiesOf(int objectId)
	{
		String v = OBJECTS.get(objectId);
		if (v == null)
		{
			return Collections.emptySet();
		}
		Set<String> out = new LinkedHashSet<>();
		Collections.addAll(out, v.split("\\+"));
		return out;
	}

	/**
	 * Facilities from an object's name when its id is not in the table (newer portal frames, other frame tiers): a portal
	 * chamber portal is named "<Destination> Portal". Never the house's exit portal or the portal nexus.
	 */
	public static Set<String> facilitiesOfName(String name)
	{
		if (name == null)
		{
			return Collections.emptySet();
		}
		String n = normal(name);
		if (!n.endsWith(" portal") || n.contains("nexus"))
		{
			return Collections.emptySet();
		}
		String dest = nexusDestination(n.substring(0, n.length() - " portal".length()));
		return dest == null ? Collections.emptySet() : Collections.singleton("poh.portal." + dest);
	}

	/**
	 * A finished house visit. A visit confirmed as the player's own (building mode seen) replaces what the house has; the nexus
	 * slots seen in its Teleport Menu replace the old ones only when the menu was read on this visit, otherwise the last read
	 * slots are kept while the nexus is still there. A visit not confirmed is the player's own house only when its layout is
	 * exactly the confirmed one (every portal, mount, box, ring and tree the same): then a nexus menu read on it updates the
	 * slots. Anything else stays apart and unused (a friend's house).
	 */
	public PohKnowledge withVisit(Set<String> seen, boolean ownHouseConfirmed, long at)
	{
		PohKnowledge k = copy();
		Set<String> v = new LinkedHashSet<>(seen);
		v.remove(NEXUS_READ_V1);
		boolean sameLayout = !ownHouseConfirmed && isConfirmed() && !v.isEmpty() && layout(v).equals(layout(k.facilities));
		if (ownHouseConfirmed || sameLayout)
		{
			Set<String> next = new LinkedHashSet<>(sameLayout ? layout(k.facilities) : layout(v));
			boolean nexus = next.contains("poh.nexus");
			if (nexus && v.contains(NEXUS_READ))
			{
				for (String f : v)
				{
					if (f.startsWith("poh.nexus.") || f.equals(NEXUS_READ))
					{
						next.add(f);
					}
				}
			}
			else if (nexus)
			{
				for (String f : k.facilities)
				{
					if (f.startsWith("poh.nexus.") || f.equals(NEXUS_READ))
					{
						next.add(f); // not read this time: the last read slots stand
					}
				}
			}
			k.facilities = next;
			if (ownHouseConfirmed)
			{
				k.confirmedAt = at;
			}
			k.unconfirmed = new LinkedHashSet<>();
			k.unconfirmedAt = 0;
		}
		else if (!v.isEmpty())
		{
			k.unconfirmed = v;
			k.unconfirmedAt = at;
		}
		return k;
	}

	private static Set<String> layout(Set<String> f)
	{
		Set<String> out = new LinkedHashSet<>();
		for (String x : f == null ? Collections.<String>emptySet() : f)
		{
			if (structural(x))
			{
				out.add(x);
			}
		}
		return out;
	}

	/**
	 * Saved knowledge from before 2026-10-22's correction: nexus slots recorded by the first collector (which also read the
	 * configuration screen) are dropped and read again from the Teleport Menu; portals, mounts, boxes, rings and trees stay.
	 */
	PohKnowledge migrated()
	{
		if (!facilities.contains(NEXUS_READ_V1) && !unconfirmed.contains(NEXUS_READ_V1))
		{
			return this;
		}
		PohKnowledge k = copy();
		k.facilities.removeIf(f -> f.equals(NEXUS_READ_V1) || f.startsWith("poh.nexus.") && !f.equals("poh.nexus"));
		k.unconfirmed.removeIf(f -> f.equals(NEXUS_READ_V1) || f.startsWith("poh.nexus.") && !f.equals("poh.nexus"));
		return k;
	}

	public PohKnowledge copy()
	{
		PohKnowledge k = new PohKnowledge();
		k.facilities = new LinkedHashSet<>(facilities == null ? Collections.emptySet() : facilities);
		k.confirmedAt = confirmedAt;
		k.unconfirmed = new LinkedHashSet<>(unconfirmed == null ? Collections.emptySet() : unconfirmed);
		k.unconfirmedAt = unconfirmedAt;
		return k;
	}

	/** A loaded copy with any outdated records migrated (see {@link #migrated()}). */
	public PohKnowledge loaded()
	{
		return copy().migrated();
	}

	public boolean isConfirmed()
	{
		return confirmedAt > 0;
	}

	public Set<String> getFacilities()
	{
		return Collections.unmodifiableSet(facilities == null ? Collections.emptySet() : facilities);
	}

	/** Facilities seen in a house not confirmed to be the player's own (shown as unresolved, never planned with). */
	public Set<String> getUnconfirmed()
	{
		return Collections.unmodifiableSet(unconfirmed == null ? Collections.emptySet() : unconfirmed);
	}

	/** YES when seen in the confirmed own house; NO when a confirmed visit did not see it; otherwise UNKNOWN. */
	public Known answer(String key)
	{
		if (key == null || !key.startsWith("poh."))
		{
			return Known.UNKNOWN;
		}
		if (!isConfirmed())
		{
			return Known.UNKNOWN;
		}
		Set<String> f = getFacilities();
		boolean menuUnread = f.contains("poh.nexus") && !f.contains(NEXUS_READ);
		if (key.startsWith(DEST))
		{
			// Any facility that sends the player there (a portal, a portal's right-click, a nexus slot).
			String d = key.substring(DEST.length());
			if (f.contains("poh.portal." + d) || f.contains("poh.portal_alt." + d) || f.contains("poh.nexus." + d))
			{
				return Known.YES;
			}
			return menuUnread ? Known.UNKNOWN : Known.NO;
		}
		if (key.startsWith("poh.nexus.") && !key.equals(NEXUS_READ))
		{
			// A nexus slot: only its Teleport Menu says (the nexus being there says nothing about its slots).
			if (f.contains(key) && f.contains(NEXUS_READ))
			{
				return Known.YES;
			}
			return menuUnread ? Known.UNKNOWN : Known.NO;
		}
		switch (key)
		{
			case "poh.jewellery_box.basic":
				return Known.of(f.contains("poh.jewellery_box.basic") || f.contains("poh.jewellery_box.fancy") || f.contains("poh.jewellery_box.ornate"));
			case "poh.jewellery_box.fancy":
				return Known.of(f.contains("poh.jewellery_box.fancy") || f.contains("poh.jewellery_box.ornate"));
			default:
				return Known.of(f.contains(key));
		}
	}

	/**
	 * The preparation page's optional house setup: what this profile's own house was confirmed to have (portals, the portal
	 * nexus's slots as its Teleport Menu listed them, the fairy ring, spirit tree, jewellery box and mounted items), and how
	 * to confirm the rest. Only a confirmed visit counts; nothing here is guessed.
	 */
	public static List<String> setupLines(PohKnowledge k, long now)
	{
		List<String> out = new ArrayList<>();
		if (k == null || !k.isConfirmed())
		{
			out.add("Your house isn't confirmed yet, so house teleports aren't planned with.");
			out.add("To confirm it: enter your house and turn on building mode once.");
			out.add("Then open the portal nexus's Teleport Menu once (if you have one) so its destinations are read.");
			return out;
		}
		Set<String> f = k.getFacilities();
		List<String> portals = new ArrayList<>();
		List<String> nexus = new ArrayList<>();
		List<String> other = new ArrayList<>();
		for (String key : f)
		{
			if (key.startsWith("poh.portal.") || key.startsWith("poh.portal_alt."))
			{
				portals.add(place(key.substring(key.indexOf('.', 4) + 1)) + (key.startsWith("poh.portal_alt.") ? " (right-click)" : ""));
			}
			else if (key.startsWith("poh.nexus.") && !key.equals(NEXUS_READ))
			{
				nexus.add(place(key.substring("poh.nexus.".length())));
			}
			else if (key.startsWith("poh.jewellery_box."))
			{
				other.add(place(key.substring("poh.jewellery_box.".length())) + " jewellery box");
			}
			else if (key.equals("poh.fairy_ring") || key.equals("poh.spirit_tree"))
			{
				other.add(key.equals("poh.fairy_ring") ? "Fairy ring" : "Spirit tree");
			}
			else if (key.startsWith("poh.mounted."))
			{
				other.add(key.endsWith("digsite") ? "Mounted digsite pendant" : "Mounted Xeric's talisman");
			}
		}
		long days = Math.max(0, (now - k.getConfirmedAt()) / 86_400_000L);
		out.add("Confirmed " + (days == 0 ? "today" : days == 1 ? "yesterday" : days + " days ago") + ". Planned with only what was seen:");
		out.add("Portals: " + (portals.isEmpty() ? "none seen" : String.join(", ", portals)));
		if (f.contains("poh.nexus"))
		{
			out.add("Portal nexus: " + (!f.contains(NEXUS_READ) ? "not read yet (open its Teleport Menu once)"
				: nexus.isEmpty() ? "no destinations listed" : String.join(", ", nexus)));
		}
		if (!other.isEmpty())
		{
			out.add("Also: " + String.join(", ", other));
		}
		out.add("Changed your house? Visit it in building mode again to update this.");
		return out;
	}

	private static String place(String key)
	{
		String s = key.replace('_', ' ');
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public EvidenceSource evidence()
	{
		PohKnowledge k = copy();
		return new EvidenceSource()
		{
			@Override
			public Known get(String key)
			{
				return k.answer(key);
			}

			@Override
			public String explain(String key)
			{
				Known a = k.answer(key);
				return a == Known.UNKNOWN ? "your house hasn't been confirmed yet (visit it and turn on building mode once)"
					: a == Known.YES ? "seen in your house" : "not seen in your house";
			}
		};
	}

	/** Two sources, the first one that knows answers. */
	public static EvidenceSource either(EvidenceSource a, EvidenceSource b)
	{
		return new EvidenceSource()
		{
			@Override
			public Known get(String key)
			{
				Known x = a == null ? Known.UNKNOWN : a.get(key);
				return x != Known.UNKNOWN || b == null ? x : b.get(key);
			}

			@Override
			public String explain(String key)
			{
				Known x = a == null ? Known.UNKNOWN : a.get(key);
				return x != Known.UNKNOWN || b == null ? (a == null ? "no evidence" : a.explain(key)) : b.explain(key);
			}
		};
	}
}
