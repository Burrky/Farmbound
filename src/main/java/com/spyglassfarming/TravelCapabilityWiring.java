package com.spyglassfarming;

import com.google.gson.Gson;
import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.AccountCapabilityAdapter;
import com.spyglassfarming.account.AccountOwner;
import com.spyglassfarming.account.EvidenceSource;
import com.spyglassfarming.account.RequirementSet;
import com.spyglassfarming.account.poh.PohKnowledge;
import com.spyglassfarming.farming.FarmingAccess;
import com.spyglassfarming.farming.FarmingAccountFacts;
import com.spyglassfarming.farming.FarmingCaptureAdapter;
import com.spyglassfarming.farming.FarmingOwner;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.farming.FarmingTravel;
import com.spyglassfarming.navigation.RouteTable;
import com.spyglassfarming.navigation.TransitionCatalog;
import com.spyglassfarming.travel.TransportAvailability;
import com.spyglassfarming.travel.TransportCatalog;
import com.spyglassfarming.travel.TransportDef;
import com.spyglassfarming.travel.TransportEvaluator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Loads the transport catalogue, tells the account adapter which quests, varps and varbits to read, and connects Farming's
 * evidence and the house to the transport evaluator (only when Farming's owner is the same account and profile).
 */
@Slf4j
@Singleton
public class TravelCapabilityWiring
{

	private final Client client;
	private final Gson gson;
	private final AccountCapabilityAdapter account;
	private final FarmingCaptureAdapter farming;
	LongSupplier clock = System::currentTimeMillis;

	private volatile TransportCatalog catalog;

	@Inject
	public TravelCapabilityWiring(Client client, Gson gson, AccountCapabilityAdapter account, FarmingCaptureAdapter farming)
	{
		this.client = client;
		this.gson = gson;
		// The bundled route table is read with the client's Gson (Plugin Hub rule), installed before anything routes.
		RouteTable.useGson(gson);
		this.account = account;
		this.farming = farming;
	}

	/** Loads the catalogue once per enable. A broken catalogue disables Phase F only, never the plugin. */
	public void start()
	{
		try
		{
			TransportCatalog loaded = TransportCatalog.load(gson);
			catalog = loaded;
			Set<Quest> quests = EnumSet.noneOf(Quest.class);
			quests.addAll(loaded.requiredQuests());
			for (RequirementSet r : FarmingAccess.all().values())
			{
				for (RequirementSet.QuestReq q : RequirementSet.list(r.quests))
				{
					quests.add(Quest.valueOf(q.quest));
				}
			}
			// Navigation (canonical §28.10): the quests, varbits and varps doors, shortcuts and boats check.
			TransitionCatalog nav = TransitionCatalog.bundled();
			quests.addAll(nav.quests());
			account.setQuestsOfInterest(quests);
			// Plus the account variables catalogue entries name as evidence ("varp:4182&16384": a built quetzal landing).
			Set<Integer> varps = new LinkedHashSet<>(nav.varbits(true));
			Set<Integer> catalogVarbits = new LinkedHashSet<>();
			for (TransportDef t : loaded.all())
			{
				for (RequirementSet.EvidenceReq e
					: RequirementSet.list(t.requirements().evidence))
				{
					Matcher m = Pattern.compile("^(varp|varbit):(\\d+)").matcher(e.key);
					if (m.find())
					{
						(m.group(1).equals("varp") ? varps : catalogVarbits).add(Integer.parseInt(m.group(2)));
					}
				}
			}
			account.setVarpsOfInterest(varps);
			// Farming's unlock varbits used by preparation (dibber / rake), read only once account data is loaded.
			// Plus the Farming Guild contract and the tool leprechaun's storage (FarmingAccountFacts).
			Set<Integer> varbits = new LinkedHashSet<>(Arrays.asList(
				VarbitID.BRUT_FARMING_PLANTING, VarbitID.FARMING_BLOCKWEEDS,
				// Fertile Soil's ultracompost upgrade (the ash covered tome; the spellbook tooltip script reads it, §28.16).
				VarbitID.FOSSIL_MINE_UNLOCKED_ULTRAFERTILESOIL));
			for (int v : FarmingAccountFacts.varbits())
			{
				varbits.add(v);
			}
			varbits.addAll(nav.varbits(false));
			varbits.addAll(catalogVarbits);
			account.setVarbitsOfInterest(varbits);
		}
		catch (RuntimeException e)
		{
			catalog = null;
			log.error("Farmbound travel: transport catalogue failed to load; account travel availability is off", e);
		}
	}

	public TransportCatalog catalog()
	{
		return catalog;
	}

	/** Every transport's availability for the current account (developer use; pure evaluation over snapshots). */
	public List<TransportAvailability> evaluate()
	{
		TransportCatalog c = catalog;
		if (c == null)
		{
			return new ArrayList<>();
		}
		AccountCapability cap = account.capability();
		return TransportEvaluator.evaluateAll(c, cap, evidence(cap.getOwner(), farming.uiSnapshot(), clock.getAsLong()), clock.getAsLong());
	}

	/** Farming's account facts (patch access, contract, tool leprechaun) for the UI, owned by the visible account. */
	public FarmingAccountFacts farmingFacts()
	{
		AccountCapability cap = account.capability();
		return FarmingAccountFacts.of(cap,
			evidence(cap == null ? null : cap.getOwner(), farming.uiSnapshot(), clock.getAsLong()));
	}

	private volatile Supplier<PohKnowledge> house;

	/** The player's observed house (phase G): its facilities answer the POH routes' evidence. */
	public void attachHouse(Supplier<PohKnowledge> house)
	{
		this.house = house;
	}

	/** The observed house's knowledge object (a new object after each visit), or null: part of a plan's inputs. */
	public Object houseKnowledge()
	{
		try
		{
			return house == null ? null : house.get();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Farming's evidence for this owner plus the observed house's (profile-scoped by its store). */
	public EvidenceSource evidence(AccountOwner owner, FarmingSnapshot snapshot, long now)
	{
		EvidenceSource f = farmingEvidence(owner, snapshot, now);
		PohKnowledge k = null;
		try
		{
			k = owner == null || house == null ? null : house.get();
		}
		catch (RuntimeException e)
		{
			// unreadable: no house evidence
		}
		return k == null ? f : PohKnowledge.either(f, k.evidence());
	}

	/** Farming's evidence only when its knowledge belongs to exactly this account and profile. */
	static EvidenceSource farmingEvidence(AccountOwner owner, FarmingSnapshot snapshot, long now)
	{
		if (owner == null || snapshot == null || snapshot.getState() != FarmingSnapshot.State.AVAILABLE)
		{
			return EvidenceSource.NONE;
		}
		FarmingOwner fo = snapshot.getOwner();
		if (fo == null || fo.getAccountHash() != owner.getAccountHash() || !fo.getProfileKey().equals(owner.getProfileKey()))
		{
			return EvidenceSource.NONE;
		}
		return FarmingTravel.evidence(snapshot.getKnowledge(), now);
	}
}
