package com.spyglassfarming;

import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.AccountCapabilityAdapter;
import com.spyglassfarming.farming.FarmingCaptureAdapter;
import com.spyglassfarming.farming.FarmingPatchCatalog;
import com.spyglassfarming.farming.FarmingPreferences;
import com.spyglassfarming.farming.FarmingPreparation;
import com.spyglassfarming.farming.FarmingPreparationView;
import com.spyglassfarming.farming.FarmingRunIntent;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.navigation.AccountRouter;
import com.spyglassfarming.prep.NeedResult;
import com.spyglassfarming.prep.PreparationEvaluator;
import com.spyglassfarming.prep.PreparationState;
import com.spyglassfarming.prep.SetOffDetector;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Plugin glue for Phase H (Bank Preparation, Tier 1): while a run's
 * preparation stage is open, re-evaluates its needs every tick from the
 * current account capability, feeds the run page and the in-bank highlight,
 * and closes the stage automatically when readiness is CONFIRMED and the
 * player closes the bank (D-F2). Advisory only: no click, withdrawal,
 * movement, typed text, script, menu action or widget change is ever made for
 * the player. Find is passive: {@link #focus} only records which item the
 * page asked about; the overlay draws it brighter, and the page explains where
 * it was last seen (reading the open bank's widgets, never changing them).
 */
@Slf4j
@Singleton
public class FarmingPreparationService
{
	private final Client client;
	private final AccountCapabilityAdapter account;
	private final FarmingCaptureAdapter farming;
	private final TravelCapabilityWiring travel;
	private final FarmingPreferenceStore preferences;
	private final FarmingPlanWorker planner;
	LongSupplier clock = System::currentTimeMillis;

	private volatile FarmingPreparationView view;
	private final SetOffDetector setOff = new SetOffDetector();

	@Inject
	public FarmingPreparationService(Client client, AccountCapabilityAdapter account, FarmingCaptureAdapter farming, TravelCapabilityWiring travel,
		FarmingPreferenceStore preferences, FarmingPlanWorker planner)
	{
		this.planner = planner;
		this.preferences = preferences;
		this.client = client;
		this.account = account;
		this.farming = farming;
		this.travel = travel;
	}

	/** The open preparation's view (any thread), or null when no run is preparing. */
	public FarmingPreparationView view()
	{
		return view;
	}

	public void reset()
	{
		view = null;
		setOff.reset();
	}

	/**
	 * Plans also refresh this often with no input changed (evidence and charges age), in ticks (30 s); off the client thread.
	 * Every input change (supplies, the run, preferences, the house, route results) asks at once.
	 */
	static final int REFRESH_TICKS = 50;
	private int ticks;

	/**
	 * The run's one plan (journeys and supplies), asked for here for preparation and guidance alike: whenever its inputs change
	 * and every few ticks, computed off the client thread; preparation's view is built from the latest one.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		Widget bank = client.getGameState() == GameState.LOGGED_IN ? client.getWidget(InterfaceID.Bankmain.ITEMS) : null;
		boolean open = bank != null && !bank.isHidden();
		FarmingSnapshot snapshot = farming.uiSnapshot();
		FarmingRunIntent intent = snapshot.getRun();
		if (snapshot.getState() != FarmingSnapshot.State.AVAILABLE || intent == null || intent.getState() != FarmingRunIntent.State.ACTIVE
			|| travel.catalog() == null)
		{
			reset();
			return;
		}
		long now = clock.getAsLong();
		AccountCapability cap = account.capability();
		FarmingPlanWorker.Key key = new FarmingPlanWorker.Key(cap, intent, preferences.get(), travel.houseKnowledge(), snapshot.getKnowledge(),
			AccountRouter.sharedVersion(), ticks++ / REFRESH_TICKS).withBins(snapshot.getBins());
		planner.request(key, travel.catalog(), FarmingPatchCatalog.standard(), () -> travel.evidence(cap.getOwner(), snapshot, now), now,
			snapshot.getKnowledge());
		FarmingPlanWorker.Result result = planner.latest();
		if (!intent.isPreparing() || result == null || !result.fits(cap, intent))
		{
			reset(); // running (guidance reads the plan), or "Getting ready": the first plan for this run is on its way
			return;
		}
		FarmingPreparation.Plan plan = result.getPlan();
		PreparationState state = PreparationEvaluator.evaluate(plan.getRequest(), cap, now);
		if (intent.getSuggestedOrder().isEmpty())
		{
			// The first plan's quickest order becomes the run's order; from then on it is the player's (the arrows change it, and
			// every later plan, the run page and guidance follow it).
			farming.setRunSuggestedOrder(plan.patchOrder());
		}
		FarmingPreparationView next = FarmingPreparationView.of(plan, state, open, now);
		view = next;
		// D-F2: readiness CONFIRMED (not just supplies carried) and the bank just closed: the player is setting off.
		if (setOff.onTick(next.getReadiness(), open))
		{
			farming.closeRunPreparation();
		}
	}
}
