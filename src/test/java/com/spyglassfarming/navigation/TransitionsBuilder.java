package com.spyglassfarming.navigation;

import net.runelite.api.Skill;
import java.util.regex.Pattern;

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
public final class TransitionsBuilder
{
	static final String DIR = "/com/spyglassfarming/navigation/";
	private static final String[][] FILES = {
		{"transports.tsv", "PASSAGE"},
		{"agility_shortcuts.tsv", "AGILITY_SHORTCUT"},
		{"boats.tsv", "BOAT"},
		{"ships.tsv", "SHIP"},
		{"quetzals.tsv", "QUETZAL"},
		{"magic_mushtrees.tsv", "MAGIC_MUSHTREE"},
		{"hot_air_balloons.tsv", "BALLOON"},
		{"spyglass_transitions.tsv", "BOAT"},
	};

	static List<Transition> load()
	{
		Map<String, int[]> itemNames = itemNames();
		List<Transition> out = new ArrayList<>();
		for (String[] f : FILES)
		{
			read(f[0], Transition.Family.valueOf(f[1]), itemNames, out);
		}
		return out;
	}

	private static Map<String, int[]> itemNames()
	{
		Map<String, int[]> out = new HashMap<>();
		List<String[]> rows = rows("item-variations.tsv");
		for (String[] row : rows.subList(1, rows.size()))
		{
			if (row.length >= 2 && !row[1].isEmpty())
			{
				String[] ids = row[1].split(",");
				int[] v = new int[ids.length];
				for (int i = 0; i < ids.length; i++)
				{
					v[i] = Integer.parseInt(ids[i].trim());
				}
				out.put(row[0].trim().toUpperCase(Locale.ROOT), v);
			}
		}
		out.putIfAbsent("COINS", new int[]{995});
		return out;
	}

	private static List<String[]> rows(String file)
	{
		List<String[]> out = new ArrayList<>();
		try (InputStream in = TransitionsBuilder.class.getResourceAsStream(DIR + file))
		{
			if (in == null)
			{
				throw new IllegalStateException("Missing navigation/" + file);
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			boolean first = true;
			while ((line = r.readLine()) != null)
			{
				if (first)
				{
					out.add(line.replaceFirst("^#\\s*", "").split("\t", -1));
					first = false;
					continue;
				}
				if (line.startsWith("#") || line.trim().isEmpty())
				{
					continue;
				}
				out.add(line.split("\t", -1));
			}
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return out;
	}

	private static void read(String file, Transition.Family family, Map<String, int[]> itemNames, List<Transition> out)
	{
		List<String[]> rows = rows(file);
		String[] header = rows.get(0);
		Map<String, Integer> col = new HashMap<>();
		for (int i = 0; i < header.length; i++)
		{
			col.put(header[i].trim(), i);
		}
		List<Object[]> departures = new ArrayList<>();
		List<Object[]> arrivals = new ArrayList<>();
		for (int i = 1; i < rows.size(); i++)
		{
			String[] row = rows.get(i);
			Integer from = point(cell(row, col, "Origin"));
			Integer to = point(cell(row, col, "Destination"));
			if (from == null && to == null)
			{
				continue;
			}
			String[] action = action(cell(row, col, "menuOption menuTarget objectID"));
			NavRequirement req = parse(cell(row, col, "Skills"), cell(row, col, "Quests"), cell(row, col, "Items"),
				cell(row, col, "Varbits"), cell(row, col, "VarPlayers"), itemNames);
			int duration = 1;
			try
			{
				String d = cell(row, col, "Duration");
				duration = d == null || d.isEmpty() ? 1 : Integer.parseInt(d.trim());
			}
			catch (NumberFormatException e)
			{
				duration = 1;
			}
			String info = cell(row, col, "Display info");
			info = info == null || info.trim().isEmpty() ? null : info.trim();
			if (from != null && to != null)
			{
				if (family == Transition.Family.SHIP && action[1] != null && action[1].equals("Monk of Entrana") && NavPoint.x(to) < 2900)
				{
					req = req.withRestriction(NavRequirement.ENTRANA); // to Entrana only: the monks search you
				}
				out.add(new Transition(from, to, family, action[0], action[1], parseInt(action[2]), req, duration, info,
					area(cell(row, col, "Arrival area"))));
			}
			else if (to == null)
			{
				departures.add(new Object[]{from, action, req, duration});
			}
			else
			{
				if (family == Transition.Family.BALLOON)
				{
					// OSRS Wiki "Balloon transport system" (2026-10-22): each landing needs a Firemaking level (boostable; the base
					// level is checked, so a boost is never assumed). Shortest Path's table has no Skills column for these rows.
					Integer fm = info == null ? null : BALLOON_FIREMAKING.get(info);
					if (fm != null)
					{
						req = req.and(parse(fm + " Firemaking", null, null, null, null, itemNames));
					}
				}
				arrivals.add(new Object[]{to, req, duration, info});
			}
		}
		for (Object[] d : departures)
		{
			for (Object[] a : arrivals)
			{
				int from = (Integer) d[0];
				int to = (Integer) a[0];
				if (NavPoint.distance(from, to) <= 5)
				{
					continue; // the landing it departs from (Shortest Path's permutation radius: quetzals and mushtrees 5)
				}
				String[] action = (String[]) d[1];
				NavRequirement req = ((NavRequirement) d[2]).and((NavRequirement) a[1]);
				if (family == Transition.Family.BALLOON)
				{
					req = req.withRestriction(NavRequirement.BALLOON_WEIGHT);
				}
				out.add(new Transition(from, to, family, action[0], action[1], parseInt(action[2]), req, Math.max((Integer) d[3], (Integer) a[2]),
					(String) a[3]));
			}
		}
	}

	/** Firemaking level per balloon landing (OSRS Wiki "Balloon transport system", 2026-10-22). */
	static final Map<String, Integer> BALLOON_FIREMAKING = new HashMap<>();

	static
	{
		BALLOON_FIREMAKING.put("Entrana", 20);
		BALLOON_FIREMAKING.put("Taverley", 20);
		BALLOON_FIREMAKING.put("Crafting Guild", 30);
		BALLOON_FIREMAKING.put("Varrock", 40);
		BALLOON_FIREMAKING.put("Castle Wars", 50);
		BALLOON_FIREMAKING.put("Grand Tree", 60);
	}

	private static String cell(String[] row, Map<String, Integer> col, String name)
	{
		Integer i = col.get(name);
		return i == null || i >= row.length ? null : row[i];
	}

	/** "x1 y1 x2 y2 plane" → an arrival area, or null. */
	static int[] area(String cell)
	{
		if (cell == null || cell.trim().isEmpty())
		{
			return null;
		}
		String[] p = cell.trim().split("\\s+");
		if (p.length != 5)
		{
			throw new IllegalStateException("Arrival area must be 'x1 y1 x2 y2 plane': " + cell);
		}
		int[] a = new int[5];
		for (int i = 0; i < 5; i++)
		{
			a[i] = Integer.parseInt(p[i]);
		}
		return a;
	}

	static Integer point(String cell)
	{
		if (cell == null || cell.trim().isEmpty())
		{
			return null;
		}
		String[] p = cell.trim().split("\\s+");
		if (p.length != 3)
		{
			return null;
		}
		return NavPoint.pack(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
	}

	/** "Climb-up Ladder 17026" → {"Climb-up", "Ladder", "17026"}. */
	static String[] action(String cell)
	{
		if (cell == null || cell.trim().isEmpty())
		{
			return new String[]{null, null, null};
		}
		String[] p = cell.trim().split("\\s+");
		if (p.length == 1)
		{
			return new String[]{p[0], null, null};
		}
		String last = p[p.length - 1];
		boolean id = last.matches("-?\\d+");
		int end = id ? p.length - 1 : p.length;
		String target = String.join(" ", Arrays.copyOfRange(p, 1, end));
		return new String[]{p[0], target.isEmpty() ? null : target, id ? last : null};
	}

	private static int parseInt(String s)
	{
		try
		{
			return s == null ? -1 : Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	static NavRequirement parse(String skillsCell, String questsCell, String itemsCell, String varbitsCell, String varpsCell,
		Map<String, int[]> itemNames)
	{
		List<NavRequirement.SkillLevel> skills = new ArrayList<>();
		List<Quest> quests = new ArrayList<>();
		List<String> questNames = new ArrayList<>();
		List<NavRequirement.ItemNeed> items = new ArrayList<>();
		List<NavRequirement.VarCheck> vars = new ArrayList<>();
		List<String> unresolved = new ArrayList<>();
		for (String part : split(skillsCell, ";"))
		{
			String[] lv = part.trim().split(" ", 2);
			Skill skill = null;
			if (lv.length == 2)
			{
				for (Skill s : Skill.values())
				{
					if (s.getName().equalsIgnoreCase(lv[1].trim()))
					{
						skill = s;
					}
				}
			}
			try
			{
				if (skill == null)
				{
					unresolved.add("skill " + part);
				}
				else if (!"Max".equals(lv[0]))
				{
					skills.add(new NavRequirement.SkillLevel(skill, Integer.parseInt(lv[0])));
				}
				else
				{
					unresolved.add("skill " + part);
				}
			}
			catch (NumberFormatException e)
			{
				unresolved.add("skill " + part);
			}
		}
		for (String name : split(questsCell, ";"))
		{
			Quest found = null;
			for (Quest q : Quest.values())
			{
				if (q.getName().equals(name.trim()))
				{
					found = q;
				}
			}
			if (found == null)
			{
				unresolved.add("quest " + name);
			}
			else
			{
				quests.add(found);
				questNames.add(name.trim());
			}
		}
		String normalized = itemsCell == null ? "" : itemsCell.replace(" ", "").replace("&&", "&").replace("||", "|").toUpperCase(Locale.ROOT);
		for (String and : split(normalized, "&"))
		{
			List<int[]> ids = new ArrayList<>();
			List<Integer> qty = new ArrayList<>();
			boolean ok = true;
			String[] ors = and.split("\\|");
			for (String or : ors)
			{
				if (or.startsWith(NavRequirement.BALLOON_LOG_STORAGE + "=") && ors.length > 1)
				{
					// Logs left in a balloon's log storage: not readable, so only the carried log counts (stricter, never looser).
					continue;
				}
				String[] nq = or.split("=");
				if (nq.length != 2 || !nq[1].matches("\\d+"))
				{
					ok = false;
					break;
				}
				int[] resolved = itemNames.get(nq[0]);
				if (resolved == null && nq[0].matches("\\d+"))
				{
					resolved = new int[]{Integer.parseInt(nq[0])};
				}
				if (resolved == null || nq[0].startsWith("UNLOCK_"))
				{
					ok = false;
					break;
				}
				ids.add(resolved);
				qty.add(Integer.parseInt(nq[1]));
			}
			if (ok)
			{
				items.add(new NavRequirement.ItemNeed(ids, qty, and));
			}
			else
			{
				unresolved.add("items " + and);
			}
		}
		for (int k = 0; k < 2; k++)
		{
			for (String part : split(k == 0 ? varbitsCell : varpsCell, ";"))
			{
				NavRequirement.VarCheck v = null;
				for (char op : new char[]{'&', '=', '>', '<'})
				{
					int at = part.indexOf(op);
					if (at > 0)
					{
						try
						{
							v = new NavRequirement.VarCheck(k == 1, Integer.parseInt(part.substring(0, at).trim()), op, Integer.parseInt(part.substring(at + 1).trim()));
						}
						catch (NumberFormatException e)
						{
							v = null;
						}
						break;
					}
				}
				if (v == null)
				{
					unresolved.add((k == 0 ? "varbit " : "varp ") + part);
				}
				else
				{
					vars.add(v);
				}
			}
		}
		if (skills.isEmpty() && quests.isEmpty() && items.isEmpty() && vars.isEmpty() && unresolved.isEmpty())
		{
			return NavRequirement.NONE;
		}
		return new NavRequirement(skills, quests, items, vars, unresolved, questNames);
	}

	/** Build tool (not shipped): writes {@code navigation/transitions.json}, which the plugin loads as is. */
	public static void main(String[] args) throws Exception
	{
		try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(args[0]), java.nio.charset.StandardCharsets.UTF_8))
		{
			w.write(new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(load())
				.replace(",\"requirement\":{\"skills\":[],\"quests\":[],\"items\":[],\"vars\":[],\"unresolved\":[],\"questNames\":[],\"restrictions\":[]}", ""));
		}
	}

	private static List<String> split(String cell, String delim)
	{
		List<String> out = new ArrayList<>();
		if (cell == null)
		{
			return out;
		}
		for (String s : cell.split(Pattern.quote(delim)))
		{
			if (!s.trim().isEmpty())
			{
				out.add(s.trim());
			}
		}
		return out;
	}
}
