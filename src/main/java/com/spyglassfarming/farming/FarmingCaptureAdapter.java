package com.spyglassfarming.farming;

import com.google.gson.Gson;
import com.spyglassfarming.Workers;
import com.spyglassfarming.storage.LocalStateStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.util.Filepath;

/**
 * The thin live adapter between the client and the pure Farming foundations
 * ({@link FarmingObserver}, {@link FarmingStore}). Registered by the plugin;
 * every handler runs on the client thread.
 *
 * <h3>Each tick</h3>
 * <ol>
 *   <li>Build the tick context WITHOUT any farming transmit value.</li>
 *   <li>{@link FarmingObserver#varbitsToRead}: empty unless this tick will be
 *   settled and eligible (logged in, profile ready and STANDARD, world type not
 *   suspended, no modal or Welcome screen, a location, not instanced, a
 *   catalogued and settled context).</li>
 *   <li>{@code client.getVarbitValue} for exactly those varbits, nothing else.</li>
 *   <li>{@link FarmingObserver#onTick} once, with the same context plus the values.</li>
 *   <li>Switch the store's owner when the observer names a new one, then
 *   {@link FarmingStore#apply} (which ignores any other owner's step).</li>
 * </ol>
 *
 * <h3>Lifecycle</h3>
 * Logout, HOPPING, LOADING, CONNECTION_LOST and a RuneScape profile change call
 * {@link FarmingObserver#discardEvidence()} at once; logout also flushes the current
 * owner's pending state. Nothing here touches player files until the observer
 * establishes an eligible owner. {@link #start()} / {@link #shutDown()} own the
 * two single-thread executors (load; log append + prune, serialized together).
 *
 * <h3>Runs (Phase E)</h3>
 * The run intent lives in {@link FarmingRunStore}, switched to the same owner
 * as the knowledge store. After each tick's knowledge update the open run is
 * re-evaluated from current knowledge ({@link FarmingRunStore#track}); logout
 * and shutdown save an active run as PAUSED. The player performs the run;
 * nothing here acts in the game.
 */
@Slf4j
@Singleton
public class FarmingCaptureAdapter implements FarmingRunControls
{
	private static final String DROPPED = "Farmbound: background task dropped after shutdown";

	private final Client client;
	private final Gson gson;
	private final LocalStateStore stateStore;
	private final FarmingPatchCatalog catalog = FarmingPatchCatalog.standard();

	// Test seams (package-private): where owner folders live, and the wall clock.
	Function<FarmingOwner, Filepath> dirs = FarmingStore.profileScopedDirs();
	LongSupplier clock = System::currentTimeMillis;

	private volatile Live live;

	/** Everything one enable owns; replaced wholesale on re-enable. Mutable fields: client thread only. */
	static final class Live
	{
		final ExecutorService loadExecutor;
		final FarmingObserver observer;
		final FarmingStore store;
		final FarmingRunStore runs;
		final Function<FarmingOwner, Filepath> dirs;
		FarmingObserverStep.Status lastStatus;
		int lastReadCount;
		int interruptions;
		String lastInterruption;
		/** The owner whose Farming the UI may show (null: none); written on the client thread, read on the EDT. */
		volatile FarmingOwner visibleOwner;
		/** Logged in where Farming is not kept (unsupported profile / suspended world type). */
		volatile boolean notAvailableHere;
		/** The catalogued patches at the player's location this tick (empty when unknown or logged out). */
		volatile Set<String> here = Collections.emptySet();
		/**
		 * The stop guidance is on now (its patch ids, in the stop's action order), for this run only: the one current target
		 * every surface leads with (canonical §28.12). Empty when guidance has none.
		 */
		volatile CurrentStop current = CurrentStop.NONE;

		Live(ExecutorService loadExecutor, FarmingObserver observer, FarmingStore store, FarmingRunStore runs,
			Function<FarmingOwner, Filepath> dirs)
		{
			this.loadExecutor = loadExecutor;
			this.observer = observer;
			this.store = store;
			this.runs = runs;
			this.dirs = dirs;
		}
	}

	@Inject
	public FarmingCaptureAdapter(Client client, Gson gson, LocalStateStore stateStore)
	{
		this.client = client;
		this.gson = gson;
		this.stateStore = stateStore;
	}

	/**
	 * Creates this enable's executors, store and observer. Touches no player
	 * file and no client API (safe from any thread): the owner's files load only
	 * once the observer has established an eligible STANDARD owner. Idempotent.
	 */
	public synchronized void start()
	{
		if (live != null)
		{
			return;
		}
		ExecutorService load = Workers.serial("spyglass-farming-load");
		Function<FarmingOwner, Filepath> ownerDirs = memoize(dirs);
		FarmingStore store = new FarmingStore(stateStore, ownerDirs, Workers.dropAfterShutdown(load, log, DROPPED), catalog);
		FarmingRunStore runs = new FarmingRunStore(stateStore, ownerDirs, Workers.dropAfterShutdown(load, log, DROPPED), catalog);
		live = new Live(load, new FarmingObserver(catalog), store, runs, ownerDirs);
	}

	/**
	 * Call after the plugin has unregistered this adapter and BEFORE
	 * {@link LocalStateStore#shutdown()}: queues the current owner's final
	 * owner-scoped write, invalidates any load still in flight (its result is
	 * dropped by generation), then drains queued log appends with a bounded wait.
	 * Idempotent.
	 */
	public void shutDown()
	{
		Live l;
		synchronized (this)
		{
			l = live;
			live = null;
		}
		if (l == null)
		{
			return;
		}
		long now = clock.getAsLong();
		l.runs.switchOwner(null, now); // an active run is saved as PAUSED
		l.store.switchOwner(null, now);
		// A load still running is dropped by generation when it finishes; nothing is interrupted.
		l.loadExecutor.shutdown();
	}

	boolean isStarted()
	{
		return live != null;
	}

	Live live()
	{
		return live;
	}

	// -- events (client thread) -------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Live l = live;
		if (l == null)
		{
			return;
		}
		long now = clock.getAsLong();
		FarmingTickInput.Builder context = context(now);
		FarmingTickInput before = context.build();
		List<Integer> plan = l.observer.varbitsToRead(before);
		// The area's compost bins are read on exactly the ticks its patches are (canonical §28.15), in the same one read.
		List<CompostBin.Def> bins = plan.isEmpty() ? Collections.emptyList() : CompostBin.withPatches(herePatches(before));
		List<Integer> reads = new ArrayList<>(plan);
		for (CompostBin.Def d : bins)
		{
			if (!reads.contains(d.getVarbit()))
			{
				reads.add(d.getVarbit());
			}
		}
		Map<Integer, Integer> values = new HashMap<>();
		for (int varbit : reads)
		{
			values.put(varbit, client.getVarbitValue(varbit));
		}
		for (int varbit : plan)
		{
			context.transmit(varbit, values.get(varbit));
		}
		FarmingTickInput input = context.build();
		FarmingObserverStep step = l.observer.onTick(input);
		l.lastReadCount = reads.size();
		l.here = herePatches(input);
		record(l, step, now);
		readBins(l, step, bins, values, now);
		track(l, step, now);
	}

	/**
	 * The compost bins of the area just read (canonical §28.15): only on a settled tick that read its patches, with the same
	 * context, so a bin is never read while settling, behind a modal or away from it.
	 */
	private static void readBins(Live l, FarmingObserverStep step, List<CompostBin.Def> bins, Map<Integer, Integer> values, long now)
	{
		if (step.getStatus() != FarmingObserverStep.Status.CAPTURED || step.getOwner() == null)
		{
			return;
		}
		for (CompostBin.Def d : bins)
		{
			Integer v = values.get(d.getVarbit());
			if (v != null)
			{
				l.store.recordBin(step.getOwner(), d.getBinId(), v, now);
			}
		}
	}

	/** Patches catalogued at the player's location (presentation only: "At Falador"). */
	private Set<String> herePatches(FarmingTickInput input)
	{
		if (!input.isLoggedIn() || input.getLocation() == null)
		{
			return Collections.emptySet();
		}
		Set<String> ids = new LinkedHashSet<>();
		for (FarmingPatchDef p : catalog.resolve(input.getLocation()).getPatches())
		{
			ids.add(p.getPatchId());
		}
		return Collections.unmodifiableSet(ids);
	}

	/**
	 * Re-derives the open run's progress after a tick that read patches
	 * (nothing about a run changes without an observation). Knowledge is
	 * copied only when a run is open.
	 */
	private void track(Live l, FarmingObserverStep step, long now)
	{
		FarmingOwner owner = step.getOwner();
		boolean read = !step.getObservations().isEmpty() || !step.getUnchangedPatchIds().isEmpty();
		if (owner == null || !l.runs.hasOpenRun(owner) || !read && !l.runs.consumeRecheck())
		{
			return;
		}
		FarmingSnapshot snapshot = l.store.snapshotFor(owner);
		if (snapshot != null)
		{
			l.runs.track(owner, snapshot.getKnowledge(), snapshot.getBins(), snapshot.getCompost(), now);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		Live l = live;
		if (l == null)
		{
			return;
		}
		GameState state = event.getGameState();
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			pendingCompost.clear(); // a choice made before a logout or hop is never credited after it
		}
		switch (state)
		{
			case LOGIN_SCREEN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				discardEvidence(l, state.name());
				l.visibleOwner = null;
				l.notAvailableHere = false;
				l.here = Collections.emptySet();
				l.runs.pause(clock.getAsLong());
				l.store.flush(clock.getAsLong());
				break;
			case HOPPING:
			case LOADING:
			case CONNECTION_LOST:
				discardEvidence(l, state.name());
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		Live l = live;
		if (l != null)
		{
			discardEvidence(l, "PROFILE_CHANGED");
		}
	}

	/** The game's own word that a gardener took a protection payment (seen in Josh's client log, 2026-10-22). */
	static final Pattern PROTECTED = Pattern.compile("You pay the gardener .+ to protect the patch\\.");

	/**
	 * A protection payment (canonical §28.13): recorded against the patch it was for. That is the stop guidance is on (or
	 * the patches here), a patch planted this run whose protection is not recorded yet; the first such patch in the stop's
	 * order when the stop has two (the message does not name the patch). Never inferred from a click or a preference.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (onCompostMessage(event.getMessage(), me == null ? null : me.getWorldLocation()))
		{
			return;
		}
		if (!PROTECTED.matcher(event.getMessage().replaceAll("<[^>]*>", "")).find())
		{
			return;
		}
		Live l = live;
		FarmingOwner visible = l == null ? null : l.visibleOwner;
		FarmingSnapshot snapshot = visible == null ? null : l.store.snapshotFor(visible);
		FarmingRunIntent intent = visible == null ? null : l.runs.intentFor(visible);
		if (snapshot == null || intent == null || !intent.isOpen())
		{
			return;
		}
		String patch = protectionCandidate(intent, catalog, snapshot.getKnowledge(), l.current.owner != null && l.current.owner.equals(visible)
			&& l.current.intentId.equals(intent.getIntentId()) ? l.current.patchIds : Collections.emptyList(), l.here);
		if (patch != null)
		{
			l.runs.recordProtected(visible, patch);
		}
	}

	// -- compost (canonical §28.14) ----------------------------------------------------------------------

	/** Compost items whose use on a patch is followed by the game's message (RuneLite CompostTracker's set, @ e98399f931c6). */
	static final Set<Integer> COMPOST_ITEMS = new HashSet<>(Arrays.asList(ItemID.BUCKET_COMPOST,
		ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST,
		ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
	/** The game's message follows the click within this (RuneLite's CompostTracker uses the same). */
	static final long COMPOST_PENDING_MILLIS = 30_000L;

	/** A patch the player chose for compost, Fertile Soil or Inspect, waiting for the game's message. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class PendingCompost
	{
		final String patchId;
		final int x1;
		final int y1;
		final int x2;
		final int y2;
		final int plane;
		final long until;

		/** Within one tile of the patch object (where the game lets the player use it). */
		boolean beside(WorldPoint p)
		{
			return p != null && p.getPlane() == plane && p.getX() >= x1 - 1 && p.getX() <= x2 + 1 && p.getY() >= y1 - 1 && p.getY() <= y2 + 1;
		}

		/** Tiles from the player to the patch object's footprint (Chebyshev; 0 on it). */
		int distance(WorldPoint p)
		{
			int dx = p.getX() < x1 ? x1 - p.getX() : p.getX() > x2 ? p.getX() - x2 : 0;
			int dy = p.getY() < y1 ? y1 - p.getY() : p.getY() > y2 ? p.getY() - y2 : 0;
			return Math.max(dx, dy);
		}
	}

	/**
	 * The choice a compost message belongs to (canonical §28.16): among the unexpired choices whose patch kind the message names,
	 * only those the player stands beside (the game acts at the patch, so a message never belongs to a patch the player is away
	 * from, however recently it was clicked); of those the nearest, and on a tie the earliest, since the game answers actions
	 * in the order they happen. A newer click on a neighbouring patch therefore never takes a delayed message for the one the
	 * player is standing at. -1 when none fits.
	 */
	static int choose(List<PendingCompost> pending, WorldPoint player, String noun, FarmingPatchCatalog catalog)
	{
		int best = -1;
		int bestD = Integer.MAX_VALUE;
		for (int i = 0; i < pending.size(); i++)
		{
			PendingCompost pc = pending.get(i);
			FarmingPatchDef def = catalog.patch(pc.patchId);
			if (def == null || !pc.beside(player) || !names(noun, def))
			{
				continue;
			}
			int d = pc.distance(player);
			if (d < bestD)
			{
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	final List<PendingCompost> pendingCompost = new ArrayList<>();

	/**
	 * The patch a compost item, Fertile Soil or Inspect was used on (client thread). Choosing is not evidence: only the
	 * game's message that follows is (see {@link #onCompostMessage}).
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		MenuAction a = e.getMenuAction();
		boolean use = false;
		if (a == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			Widget w = client.getSelectedWidget();
			use = w != null && (COMPOST_ITEMS.contains(w.getItemId()) || w.getId() == InterfaceID.MagicSpellbook.FERTILE_SOIL);
		}
		else if (a == MenuAction.GAME_OBJECT_FIRST_OPTION || a == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| a == MenuAction.GAME_OBJECT_THIRD_OPTION || a == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| a == MenuAction.GAME_OBJECT_FIFTH_OPTION)
		{
			use = "Inspect".equals(e.getMenuOption());
		}
		if (!use)
		{
			return;
		}
		ObjectComposition def = client.getObjectDefinition(e.getId());
		WorldView view = client.getTopLevelWorldView();
		if (def == null || def.getVarbitId() <= 0 || view == null)
		{
			return;
		}
		WorldPoint at = WorldPoint.fromScene(view, e.getParam0(), e.getParam1(), view.getPlane());
		FarmingPatchDef patch = null;
		for (FarmingPatchDef p : catalog.resolve(new FarmingLocation(at.getX(), at.getY(), at.getPlane())).getPatches())
		{
			if (p.getVarbit() == def.getVarbitId())
			{
				patch = p;
			}
		}
		if (patch == null)
		{
			return;
		}
		int w = Math.max(1, def.getSizeX());
		int h = Math.max(1, def.getSizeY());
		long now = clock.getAsLong();
		String chosen = patch.getPatchId();
		pendingCompost.removeIf(pc -> pc.until < now || pc.patchId.equals(chosen)); // one choice per patch: the newest
		pendingCompost.add(new PendingCompost(patch.getPatchId(), at.getX(), at.getY(), at.getX() + w - 1, at.getY() + h - 1, at.getPlane(),
			now + COMPOST_PENDING_MILLIS));
	}

	/** The game's compost message, credited to the patch the player chose and stands beside. True when recorded. */
	boolean onCompostMessage(String message, WorldPoint player)
	{
		PatchCompost.Message m = PatchCompost.parse(message);
		Live l = live;
		if (m == null || l == null || l.visibleOwner == null)
		{
			return false;
		}
		long now = clock.getAsLong();
		pendingCompost.removeIf(pc -> pc.until < now);
		int i = player == null ? -1 : choose(pendingCompost, player, m.patchNoun, catalog);
		if (i < 0)
		{
			return false;
		}
		PendingCompost pc = pendingCompost.remove(i);
		FarmingPatchDef def = catalog.patch(pc.patchId);
		Map<String, PatchKnowledge> k = l.store.snapshot();
		l.store.recordCompost(l.visibleOwner, pc.patchId, PatchCompost.of(m, now, k.get(pc.patchId), def), now);
		return true;
	}

	/** The message's patch noun ("herb patch", "allotment", "calquat tree patch") fits this patch's type. */
	static boolean names(String noun, FarmingPatchDef def)
	{
		String n = noun == null ? "" : noun.toLowerCase(Locale.ROOT);
		String t = def.getType().getDisplayName().toLowerCase(Locale.ROOT);
		return n.contains(t) || t.contains(n.replace(" patch", "").trim()) || n.contains(t.split(" ")[0]);
	}

	static String protectionCandidate(FarmingRunIntent intent, FarmingPatchCatalog catalog, Map<String, PatchKnowledge> knowledge, List<String> current,
		Set<String> here)
	{
		FarmingRunEvaluator.Progress progress = FarmingRunEvaluator.evaluate(intent, catalog, knowledge);
		List<String> order = new ArrayList<>(current);
		for (String id : here)
		{
			if (!order.contains(id))
			{
				order.add(id);
			}
		}
		for (String id : order)
		{
			FarmingRunEvaluator.PatchProgress p = progress.patch(id);
			if (p != null && (here.contains(id) || current.contains(id)) && !intent.getProtectedPatchIds().contains(id)
				&& (p.getDetail() == FarmingRunEvaluator.Detail.REPLANTED || p.getDetail() == FarmingRunEvaluator.Detail.PLANTED_UNSEEN))
			{
				return id;
			}
		}
		return null;
	}

	// -- tick context -------------------------------------------------------------

	/** The tick's context, never any farming transmit value. Unavailable data stays unavailable. */
	private FarmingTickInput.Builder context(long now)
	{
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		FarmingTickInput.Builder b = FarmingTickInput.builder().tick(client.getTickCount()).epochMillis(now).loggedIn(loggedIn);
		if (!loggedIn)
		{
			return b;
		}
		b.accountHash(client.getAccountHash())
			.world(client.getWorld())
			.worldTypes(client.getWorldType())
			.profileType(RuneScapeProfileType.getCurrent(client))
			.welcomeScreenVisible(isShown(client.getWidget(InterfaceID.WelcomeScreen.MOTW)))
			.modalOpen(isModalOpen());
		WorldView view = client.getTopLevelWorldView();
		Player player = client.getLocalPlayer();
		if (view != null && player != null)
		{
			b.instanced(view.isInstance());
			WorldPoint at = player.getWorldLocation();
			if (at != null)
			{
				b.location(new FarmingLocation(at.getX(), at.getY(), at.getPlane()));
			}
		}
		return b;
	}

	private static boolean isShown(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	/** RuneLite FarmingTracker: varbits are not sent while a modal is open. An unreadable table counts as blocked. */
	private boolean isModalOpen()
	{
		HashTable<WidgetNode> table = client.getComponentTable();
		if (table == null)
		{
			return true;
		}
		for (WidgetNode node : table)
		{
			if (node.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				return true;
			}
		}
		return false;
	}

	// -- applying a step ----------------------------------------------------------

	private void record(Live l, FarmingObserverStep step, long now)
	{
		FarmingObserverStep.Status status = step.getStatus();
		if (status != l.lastStatus)
		{
			log.debug("Farmbound: {} -> {}", l.lastStatus, status);
			l.lastStatus = status;
		}
		FarmingOwner owner = step.getOwner();
		updateVisibility(l, status, owner);
		if (owner != null && !owner.equals(l.store.getOwner()))
		{
			log.debug("Farmbound: owner is now {} (loading {})", owner, l.dirs.apply(owner));
			l.store.switchOwner(owner, now);
			l.runs.switchOwner(owner, now);
		}
		l.store.apply(step);
		for (PatchObservation o : step.getObservations())
		{
			log.debug("Farmbound: {} {} {}", o.getKind(), o.getPatch().getPatchId(), o.getReading().getRaw());
		}
		for (CalibrationSample s : step.getCalibrationSamples())
		{
			log.debug("Farmbound: growth tick {} {} stage {} -> {}", s.getPatchId(), s.getProduce(), s.getFromStage(), s.getToStage());
		}
	}

	/**
	 * What the UI may show: the step's owner once established; nothing while
	 * logged out or where Farming is not kept. A transient not-ready tick (every
	 * interruption re-checks profile readiness) keeps the current owner, so the
	 * page does not flicker on each loading screen; an account change always
	 * passes through logged-out first.
	 */
	private static void updateVisibility(Live l, FarmingObserverStep.Status status, FarmingOwner owner)
	{
		switch (status)
		{
			case NOT_LOGGED_IN:
				l.visibleOwner = null;
				l.notAvailableHere = false;
				break;
			case UNSUPPORTED_PROFILE:
			case WORLD_TYPE_SUSPENDED:
				l.visibleOwner = null;
				l.notAvailableHere = true;
				break;
			case PROFILE_NOT_READY:
				break;
			default:
				if (owner != null)
				{
					l.visibleOwner = owner;
					l.notAvailableHere = false;
				}
				break;
		}
	}

	public FarmingSnapshot uiSnapshot()
	{
		Live l = live;
		if (l == null)
		{
			return FarmingSnapshot.LOGGED_OUT;
		}
		FarmingOwner visible = l.visibleOwner;
		if (visible == null)
		{
			return l.notAvailableHere
				? new FarmingSnapshot(FarmingSnapshot.State.NOT_AVAILABLE_HERE, null, Collections.emptyMap(), Collections.emptyList())
				: FarmingSnapshot.LOGGED_OUT;
		}
		FarmingSnapshot snapshot = l.store.snapshotFor(visible);
		if (snapshot == null || !l.runs.isLoaded() || !visible.equals(l.runs.getOwner()))
		{
			return new FarmingSnapshot(FarmingSnapshot.State.LOADING, visible, Collections.emptyMap(), Collections.emptyList());
		}
		FarmingRunIntent intent = l.runs.intentFor(visible);
		CurrentStop c = l.current;
		List<String> current = intent != null && c.owner != null && c.owner.equals(visible) && c.intentId.equals(intent.getIntentId())
			? c.patchIds : Collections.emptyList();
		return snapshot.withRun(intent, l.here, current);
	}

	// -- run actions (EDT; each acts only for the owner the UI is showing) ------------------

	/** Start Run from the preview: validated, then persisted with a baseline copied from current knowledge. */
	@Override
	public FarmingRunStore.StartResult startRun(FarmingRunRequest request)
	{
		Live l = live;
		FarmingOwner visible = l == null ? null : l.visibleOwner;
		FarmingSnapshot snapshot = visible == null ? null : l.store.snapshotFor(visible);
		if (snapshot == null)
		{
			return FarmingRunStore.StartResult.refused("Log in on a standard world to start a run.");
		}
		return l.runs.start(visible, request, snapshot.getKnowledge(), clock.getAsLong());
	}

	@Override
	public void setSkipped(String patchId, boolean skip)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setSkipped(l.visibleOwner, patchId, skip, clock.getAsLong());
		}
	}

	@Override
	public void setBinSkipped(String binId, boolean skip)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setBinSkipped(l.visibleOwner, binId, skip, clock.getAsLong());
		}
	}

	@Override
	public void setCompostSkipped(String patchId, boolean skip)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setCompostSkipped(l.visibleOwner, patchId, skip, clock.getAsLong());
		}
	}

	@Override
	public void setProtectionSkipped(String patchId, boolean skip)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setProtectionSkipped(l.visibleOwner, patchId, skip, clock.getAsLong());
		}
	}

	@Override
	public void resumeRun()
	{
		Live l = live;
		if (l != null)
		{
			l.runs.resume(l.visibleOwner, clock.getAsLong());
		}
	}

	@Override
	public void closeRunPreparation()
	{
		Live l = live;
		if (l != null)
		{
			l.runs.closePreparation(l.visibleOwner);
		}
	}

	@Override
	public void setRunBounty(boolean use)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setUseBounty(l.visibleOwner, use);
		}
	}

	@Override
	public void setRunCompost(FarmingPatchType type, FarmingCompost choice)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setCompost(l.visibleOwner, type, choice);
		}
	}

	/** The current guidance target for the visible owner's run (client thread); empty or null clears it. */
	public void setRunCurrentStop(FarmingRunIntent intent, List<String> patchIds)
	{
		Live l = live;
		if (l == null)
		{
			return;
		}
		CurrentStop next = intent == null || patchIds == null || patchIds.isEmpty() || l.visibleOwner == null ? CurrentStop.NONE
			: new CurrentStop(l.visibleOwner, intent.getIntentId(), patchIds);
		if (!next.equals(l.current))
		{
			l.current = next;
		}
	}

	/** Who the current stop belongs to: one owner's one run. */
	static final class CurrentStop
	{
		static final CurrentStop NONE = new CurrentStop(null, "", Collections.emptyList());
		final FarmingOwner owner;
		final String intentId;
		final List<String> patchIds;

		CurrentStop(FarmingOwner owner, String intentId, List<String> patchIds)
		{
			this.owner = owner;
			this.intentId = intentId;
			this.patchIds = Collections.unmodifiableList(new ArrayList<>(patchIds));
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof CurrentStop && Objects.equals(owner, ((CurrentStop) o).owner) && intentId.equals(((CurrentStop) o).intentId)
				&& patchIds.equals(((CurrentStop) o).patchIds);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(owner, intentId, patchIds);
		}
	}

	@Override
	public void setRunSuggestedOrder(List<String> patchIds)
	{
		Live l = live;
		if (l != null)
		{
			l.runs.setSuggestedOrder(l.visibleOwner, patchIds);
		}
	}

	@Override
	public void endRun()
	{
		Live l = live;
		FarmingOwner visible = l == null ? null : l.visibleOwner;
		FarmingSnapshot snapshot = visible == null ? null : l.store.snapshotFor(visible);
		if (snapshot != null)
		{
			l.runs.end(visible, snapshot.getKnowledge(), clock.getAsLong());
		}
	}

	@Override
	public void acknowledgeRun()
	{
		Live l = live;
		if (l != null)
		{
			l.runs.acknowledge(l.visibleOwner);
		}
	}

	private void discardEvidence(Live l, String why)
	{
		l.observer.discardEvidence();
		l.interruptions++;
		l.lastInterruption = why;
		if (!"LOADING".equals(why))
		{
			log.debug("Farmbound: interrupted ({})", why);
		}
	}

	// -- executors ----------------------------------------------------------------

	/** One directory resolution (and creation) per owner per enable, not one per write. */
	private static Function<FarmingOwner, Filepath> memoize(Function<FarmingOwner, Filepath> f)
	{
		Map<FarmingOwner, Filepath> cache = Collections.synchronizedMap(new HashMap<>());
		return owner -> cache.computeIfAbsent(owner, f);
	}
}
