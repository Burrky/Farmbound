package com.spyglassfarming.travel;

import com.google.gson.Gson;
import com.spyglassfarming.account.RequirementSet;
import com.spyglassfarming.navigation.RouteTable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;

/**
 * The transport registry (canonical §11): one generated resource, {@code travel/catalog.json}, with every catalogued
 * teleport, the house network routes and the world fairy ring and spirit tree journeys, already validated by the build tool
 * that writes it (the test-side {@code CatalogBuilder}, from the family files). Generic: it knows nothing about Farming.
 */
public final class TransportCatalog
{
	/** A fairy ring or spirit tree the player can walk up to and use as an entrance. */
	public static final class NetworkNode
	{
		public String id;
		public String network;
		public String code;
		public String name;
		public int x;
		public int y;
		public int plane;
		/** Requirements to use this entrance beyond the network's own (quests, proofs, evidence for a grown tree). */
		public RequirementSet requirements;
		/** A player-grown spirit tree: tile and requirements come from this catalogued transport. */
		public String fromTransport;
	}

	static final class Doc
	{
		int schema;
		List<TransportDef> transports;
		List<NetworkNode> nodes;
		/** World network journeys, built at load: teleport id → entrance id → walk tiles from its arrival to the entrance. */
		Map<String, Map<String, Integer>> journeys;
	}

	private static List<NetworkNode> nodes = Collections.emptyList();
	private final Map<String, TransportDef> byId = new LinkedHashMap<>();

	TransportCatalog(List<TransportDef> transports, List<NetworkNode> entrances)
	{
		for (TransportDef t : transports)
		{
			if (byId.put(t.id, t) != null)
			{
				throw new IllegalArgumentException("Duplicate transport id " + t.id);
			}
		}
		nodes = Collections.unmodifiableList(entrances);
	}

	/** Loads the catalogue; throws when it is missing or unreadable (a build-time defect). */
	public static TransportCatalog load(Gson gson)
	{
		RouteTable.useGson(gson);
		try (InputStream in = TransportCatalog.class.getResourceAsStream("/com/spyglassfarming/travel/catalog.json"))
		{
			return load(gson, new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Unreadable transport catalogue", e);
		}
	}

	static TransportCatalog load(Gson gson, String json)
	{
		Doc d = gson.fromJson(json, Doc.class);
		Map<String, Object> by = new LinkedHashMap<>();
		d.transports.forEach(t -> by.put(t.id, t));
		d.nodes.forEach(n -> by.put(n.id, n));
		List<TransportDef> all = new ArrayList<>(d.transports);
		d.journeys.forEach((entry, walks) -> walks.forEach((node, walk) ->
		{
			NetworkNode n = (NetworkNode) by.get(node);
			for (TransportDef hop : d.transports)
			{
				// Every destination of the entrance's network but the entrance itself.
				if (n.network.equals(hop.network) && hop.arrival != null && (n.plane != hop.arrival.plane
					|| Math.max(Math.abs(n.x - hop.arrival.x), Math.abs(n.y - hop.arrival.y)) > 10))
				{
					all.add(journey(gson, (TransportDef) by.get(entry), n, hop, walk));
				}
			}
		}));
		return new TransportCatalog(all, d.nodes);
	}

	/** A teleport, the walk from where it lands to a network entrance, and a destination on that network (canonical §28.11). */
	static TransportDef journey(Gson gson, TransportDef entry, NetworkNode n, TransportDef hop, int walk)
	{
		TransportDef d = gson.fromJson(gson.toJson(entry), TransportDef.class);
		boolean ring = "FAIRY_RING".equals(hop.network);
		String code = ring ? hop.id.substring(hop.id.indexOf('.') + 1).toUpperCase(Locale.ROOT)
			: hop.name.replaceFirst("^[^:]*:\\s*", "");
		d.id = "via." + entry.id + "." + n.id + "." + hop.id;
		d.name = entry.name + ", then " + (ring ? "fairy ring " + n.code + " to " + code
			: Character.toLowerCase(n.name.charAt(0)) + n.name.substring(1) + " to " + code);
		d.arrival = hop.arrival;
		d.wield = hop.wield;
		d.preWalk = walk;
		d.walkTo = "node:" + n.id;
		d.walkFrom = "arr:" + entry.id;
		RequirementSet r = new RequirementSet();
		for (RequirementSet q : new RequirementSet[]{entry.requirements(), n.requirements == null ? new RequirementSet() : n.requirements, hop.requirements()})
		{
			r.skills = concat(r.skills, q.skills);
			r.quests = concat(r.quests, q.quests);
			r.diaries = concat(r.diaries, q.diaries);
			r.evidence = concat(r.evidence, q.evidence);
			r.proofs = concat(r.proofs, q.proofs);
			r.spellbook = r.spellbook != null ? r.spellbook : q.spellbook;
			r.members = r.members == null || q.members == null ? null : r.members || q.members;
		}
		d.requirements = r;
		TransportDef.Via v = new TransportDef.Via();
		v.entry = entry.id;
		v.entryAt = entry.arrival;
		v.node = n.id;
		v.nodeName = n.name;
		v.nodeAt = new TransportDef.Point();
		v.nodeAt.x = n.x;
		v.nodeAt.y = n.y;
		v.nodeAt.plane = n.plane;
		v.hop = hop.id;
		v.network = hop.network;
		v.code = code;
		d.via = v;
		List<String> notes = new ArrayList<>(TransportDef.list(entry.notes));
		notes.add("Walk to the " + n.name + ", then " + (ring ? "dial " + code : "travel to " + code) + ".");
		d.notes = notes;
		return d;
	}

	private static <T> List<T> concat(List<T> a, List<T> b)
	{
		if ((a == null || a.isEmpty()) && (b == null || b.isEmpty()))
		{
			return null;
		}
		List<T> out = new ArrayList<>(RequirementSet.list(a));
		for (T t : RequirementSet.list(b))
		{
			if (!out.contains(t))
			{
				out.add(t);
			}
		}
		return out;
	}

	/** The entrances loaded with the catalogue. */
	public static List<NetworkNode> nodes()
	{
		return nodes;
	}

	public List<TransportDef> all()
	{
		return new ArrayList<>(byId.values());
	}

	public TransportDef get(String id)
	{
		return byId.get(id);
	}

	/** The quests any requirement names: the account adapter reads only these. */
	public Set<Quest> requiredQuests()
	{
		Set<Quest> out = EnumSet.noneOf(Quest.class);
		for (TransportDef t : byId.values())
		{
			RequirementSet r = t.requirements();
			for (RequirementSet.QuestReq q : RequirementSet.list(r.quests))
			{
				out.add(Quest.valueOf(q.quest));
			}
			for (RequirementSet.ProofReq p : RequirementSet.list(r.proofs))
			{
				RequirementSet.list(p.anyQuestFinished).forEach(q -> out.add(Quest.valueOf(q)));
				RequirementSet.list(p.anyQuestStarted).forEach(q -> out.add(Quest.valueOf(q)));
			}
		}
		return out;
	}
}
