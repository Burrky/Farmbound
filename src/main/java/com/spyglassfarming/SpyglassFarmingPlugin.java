package com.spyglassfarming;

import com.google.inject.Provides;
import com.spyglassfarming.account.AccountCapabilityAdapter;
import com.spyglassfarming.account.poh.PohKnowledge;
import com.spyglassfarming.farming.FarmingAccountFacts;
import com.spyglassfarming.farming.FarmingCaptureAdapter;
import com.spyglassfarming.farming.FarmingPreferences;
import com.spyglassfarming.farming.FarmingPreparation;
import com.spyglassfarming.farming.FarmingPreparationView;
import com.spyglassfarming.farming.FarmingRunControls;
import com.spyglassfarming.farming.FarmingRunIntent;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.guidance.GuidanceModel;
import com.spyglassfarming.storage.LocalStateStore;
import com.spyglassfarming.storage.TelemetryPaths;
import com.spyglassfarming.ui.SpyglassFarmingPanel;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Farming Companion on its own: the patch overview with crop cards, single and combined runs, preparation (supplies and the
 * teleports for each stop on one checklist, your own stop order), travel guidance (which teleport, a blue walking path, the
 * patch action and highlight on arrival) and compost. It reads the game (varbits, items, chat, the scene) only;
 * it never acts in the game and needs no other plugin.
 */
@Slf4j
@PluginDescriptor(
	name = "Farmbound",
	description = "Farming runs: patch overview, combined runs, supplies and teleports, your stop order, a walking path and the next action",
	tags = {"farming", "herb", "tree", "compost", "run", "patches", "teleport"},
	internalName = SpyglassFarmingPlugin.INTERNAL_NAME
)
public class SpyglassFarmingPlugin extends Plugin
{
	/**
	 * This plugin's identity: RuneLite keeps its data in {@code .runelite/plugin-data/spyglass-farming}, apart from Spyglass's
	 * {@code plugin-data/spyglass}. The Plugin Hub requires it to equal the plugin's Hub file name, which may use only lowercase
	 * letters, digits and hyphens, so a dev launch and a Hub install share one folder. No legacy folder is declared: RuneLite
	 * would move that folder in, and Spyglass's records are never moved.
	 */
	static final String INTERNAL_NAME = "spyglass-farming";

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private LocalStateStore stateStore;

	@Inject
	private FarmingCaptureAdapter adapter;

	@Inject
	private AccountCapabilityAdapter account;

	@Inject
	private TravelCapabilityWiring travel;

	@Inject
	private FarmingPlanWorker planner;

	@Inject
	private FarmingPreparationService preparation;

	@Inject
	private FarmingPreferenceStore preferences;

	@Inject
	private PohObserver house;

	@Inject
	private ChargeObserver charges;

	@Inject
	private GuidanceService guidance;

	@Inject
	private ItemManager itemManager;


	@Inject
	private ConfigManager configManager;

	@Inject
	private SpyglassFarmingConfig config;

	private SpyglassFarmingPanel panel;
	private NavigationButton navButton;
	/** Only what was started is stopped (a start-up that failed part way is undone safely). */
	private boolean servicesStarted;

	@Provides
	SpyglassFarmingConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(SpyglassFarmingConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		TelemetryPaths.init(getPluginDirectory());
		stateStore.start();
		servicesStarted = true;
		adapter.start();
		eventBus.register(adapter);
		// Travel: what the account can use (quests, skills, items, diaries, the house), the shared run plan, preparation and
		// guidance, in the order the original Farming wiring started them.
		account.start();
		travel.start();
		eventBus.register(account);
		eventBus.register(travel);
		planner.start();
		preparation.reset();
		eventBus.register(preparation);
		preferences.load();
		eventBus.register(preferences);
		house.load();
		eventBus.register(house);
		charges.load();
		eventBus.register(charges);
		account.setChargeSource(charges::knowledge);
		travel.attachHouse(house::knowledge);
		guidance.start();
		eventBus.register(guidance);
		panel = new SpyglassFarmingPanel(itemManager, new Host());
		panel.rebuild(config.textSize().getScale());
		navButton = NavigationButton.builder().tooltip("Farmbound").icon(icon()).priority(7).panel(panel).build();
		addUi(navButton);
	}

	/** Puts the sidebar button and the highlight overlay on screen (a seam: tests record instead of drawing). */
	void addUi(NavigationButton button)
	{
		clientToolbar.addNavigation(button);
		guidance.overlays().addTo(overlayManager);
	}

	void removeUi(NavigationButton button)
	{
		if (guidance.overlays() != null)
		{
			guidance.overlays().removeFrom(overlayManager);
		}
		clientToolbar.removeNavigation(button);
	}

	/** Safe after a partial start-up (RuneLite calls this when startUp throws): only what was set up is taken down. */
	@Override
	protected void shutDown()
	{
		NavigationButton button = navButton;
		navButton = null;
		if (button != null)
		{
			removeUi(button);
		}
		SpyglassFarmingPanel p = panel;
		panel = null;
		if (p != null)
		{
			p.stop();
		}
		for (Object o : new Object[]{guidance, charges, house, preferences, preparation, travel, account, adapter})
		{
			eventBus.unregister(o);
		}
		if (servicesStarted)
		{
			servicesStarted = false;
			guidance.stop();
			planner.stop();
			preparation.reset();
			account.shutDown();
			adapter.shutDown();
			stateStore.shutdown();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		SpyglassFarmingPanel p = panel;
		if (p != null && SpyglassFarmingConfig.GROUP.equals(e.getGroup()) && "textSize".equals(e.getKey()))
		{
			float scale = config.textSize().getScale();
			SwingUtilities.invokeLater(() -> p.rebuild(scale));
		}
	}

	/** The panel's view of the plugin: the run, the account facts, preparation's plan, guidance and the saved choices. */
	private final class Host implements SpyglassFarmingPanel.Host
	{
		@Override
		public FarmingSnapshot snapshot()
		{
			return adapter.uiSnapshot();
		}

		@Override
		public FarmingAccountFacts facts()
		{
			return travel.farmingFacts();
		}

		@Override
		public FarmingRunControls controls()
		{
			return adapter;
		}

		@Override
		public FarmingPreparationView preparation()
		{
			return preparation.view();
		}

		@Override
		public GuidanceModel guidance()
		{
			return guidance.model();
		}

		@Override
		public FarmingPreferences preferences()
		{
			return preferences.get();
		}

		@Override
		public void savePreferences(FarmingPreferences p)
		{
			preferences.set(p);
		}

		@Override
		public String selection()
		{
			try
			{
				return configManager.getConfiguration(SpyglassFarmingConfig.GROUP, "selection");
			}
			catch (RuntimeException e)
			{
				return null;
			}
		}

		@Override
		public void saveSelection(String s)
		{
			try
			{
				configManager.setConfiguration(SpyglassFarmingConfig.GROUP, "selection", s);
			}
			catch (RuntimeException e)
			{
				log.debug("Farming selection not saved", e);
			}
		}

		@Override
		public void moveStop(int index, int delta)
		{
			FarmingRunIntent run = adapter.uiSnapshot().getRun();
			FarmingPlanWorker.Result plan = planner.latest();
			if (run != null && plan != null)
			{
				List<String> order = FarmingPreparation.moved(plan.getPlan().getSites(), index, delta);
				adapter.setRunSuggestedOrder(order);
			}
		}

		@Override
		public List<String> houseSetup()
		{
			return PohKnowledge.setupLines(house.knowledge(), System.currentTimeMillis());
		}

		@Override
		public long now()
		{
			return System.currentTimeMillis();
		}
	}

	/** The toolbar sprite: a two-leaf sprout on a diamond of tilled soil (16×16, transparent). */
	private static BufferedImage icon()
	{
		return ImageUtil.loadImageResource(SpyglassFarmingPlugin.class, "farmbound.png");
	}
}
