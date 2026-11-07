package com.spyglassfarming.navigation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;

/**
 * Every transition walking may use (canonical §28.10), from the Shortest Path plugin's transport tables (Skretzo and
 * contributors, BSD 2-Clause; see THIRD_PARTY_NOTICES.md), bundled under {@code navigation/}: {@code transports.tsv} (doors,
 * gates, stairs, ladders, caves), {@code agility_shortcuts.tsv}, {@code boats.tsv}, {@code ships.tsv},
 * {@code quetzals.tsv}, {@code magic_mushtrees.tsv} and {@code hot_air_balloons.tsv}. Teleports, fairy rings and spirit trees are not here: they are the
 * travel catalogue's ({@code travel/TransportCatalog}), which evaluates their items, charges and requirements.
 *
 * <p>Columns are read by their header names. In the network tables (quetzals, mushtrees) a row with no destination is a
 * departure point and a row with no origin an arrival point; every departure reaches every other arrival.
 *
 * <p>Immutable after loading; thread-safe.
 */
public final class TransitionCatalog
{
	private final List<Transition> all;
	private final Map<Integer, List<Transition>> byFrom;
	private final Map<Integer, List<Transition>> byTo;

	private TransitionCatalog(List<Transition> all)
	{
		this.all = Collections.unmodifiableList(all);
		Map<Integer, List<Transition>> f = new HashMap<>();
		Map<Integer, List<Transition>> t = new HashMap<>();
		for (Transition tr : all)
		{
			tr.requirement = tr.requirement == null ? NavRequirement.NONE : tr.requirement; // the data leaves out "none"
			f.computeIfAbsent(tr.from, k -> new ArrayList<>(2)).add(tr);
			t.computeIfAbsent(tr.to, k -> new ArrayList<>(2)).add(tr);
		}
		this.byFrom = f;
		this.byTo = t;
	}

	private static volatile TransitionCatalog shared;

	public static TransitionCatalog bundled()
	{
		TransitionCatalog c = shared;
		if (c == null)
		{
			synchronized (TransitionCatalog.class)
			{
				c = shared;
				if (c == null)
				{
					try (InputStream in = TransitionCatalog.class.getResourceAsStream("/com/spyglassfarming/navigation/transitions.json"))
					{
						c = new TransitionCatalog(new ArrayList<>(Arrays.asList(RouteTable.gson().fromJson(new String(in.readAllBytes(),
							StandardCharsets.UTF_8), Transition[].class))));
					}
					catch (IOException e)
					{
						throw new UncheckedIOException(e);
					}
					shared = c;
				}
			}
		}
		return c;
	}

	/** A catalogue of given transitions (tests). */
	static TransitionCatalog of(List<Transition> transitions)
	{
		return new TransitionCatalog(new ArrayList<>(transitions));
	}

	public List<Transition> all()
	{
		return all;
	}

	public List<Transition> from(int packed)
	{
		List<Transition> l = byFrom.get(packed);
		return l == null ? Collections.emptyList() : l;
	}

	/** Every varbit and varp any transition checks (read once account data is loaded). */
	public Set<Integer> varbits(boolean varps)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (Transition t : all)
		{
			for (NavRequirement.VarCheck v : t.requirement.getVars())
			{
				if (v.varp == varps)
				{
					out.add(v.id);
				}
			}
		}
		return out;
	}

	/** Every quest any transition needs. */
	public Set<Quest> quests()
	{
		Set<Quest> out = new LinkedHashSet<>();
		for (Transition t : all)
		{
			out.addAll(t.requirement.getQuests());
		}
		return out;
	}
}
