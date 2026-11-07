package com.spyglassfarming.ui;

import static com.spyglassfarming.farming.RunHarness.A;
import static com.spyglassfarming.farming.RunHarness.B;
import static com.spyglassfarming.farming.RunHarness.C;
import static com.spyglassfarming.farming.RunHarness.D;
import static com.spyglassfarming.farming.RunHarness.HERB_SITE;
import static com.spyglassfarming.farming.RunHarness.TREE_SITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.spyglassfarming.ChargeObserver;
import com.spyglassfarming.FarmingPlanWorker;
import com.spyglassfarming.FarmingPreferenceStore;
import com.spyglassfarming.FarmingPreparationService;
import com.spyglassfarming.GuidanceService;
import com.spyglassfarming.NavigationRuntime;
import com.spyglassfarming.PohObserver;
import com.spyglassfarming.TravelCapabilityWiring;
import com.spyglassfarming.account.AccountCapability;
import com.spyglassfarming.account.AccountCapabilityAdapter;
import com.spyglassfarming.account.AccountFixtures;
import com.spyglassfarming.account.AccountReaders;
import com.spyglassfarming.account.poh.PohKnowledge;
import com.spyglassfarming.farming.FarmingAccountFacts;
import com.spyglassfarming.farming.FarmingCompost;
import com.spyglassfarming.farming.FarmingModel;
import com.spyglassfarming.farming.FarmingPatchDef;
import com.spyglassfarming.farming.FarmingPatchType;
import com.spyglassfarming.farming.FarmingPreferences;
import com.spyglassfarming.farming.FarmingPreparation;
import com.spyglassfarming.farming.FarmingPreparationView;
import com.spyglassfarming.farming.FarmingRunControls;
import com.spyglassfarming.farming.FarmingRunIntent;
import com.spyglassfarming.farming.FarmingRunType;
import com.spyglassfarming.farming.FarmingSites;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.farming.RunHarness;
import com.spyglassfarming.guidance.GuidanceModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

/**
 * The whole loop through the real sidebar and the real travel stack (transport catalogue, account router, live route search,
 * plan worker, preparation service, guidance service), driven by a fake client: choose herb + tree in Combine mode with
 * supercompost, see the teleports on the preparation checklist beside the seeds and tools, begin, follow the NEXT card and the
 * blue walking path, take a detour and get a fresh path, arrive beside the patch and get the patch action and highlight. Then a
 * teleport stops being usable and preparation and NEXT both move to another way. Each screen is drawn at the sidebar's real
 * width (225 px) at Standard and Extra large text into build/screens. Synthetic data only.
 */
public class GuidanceFlowTest
{
	private static final int HERB_RIPE = 36;
	private static final int TREE_RIPE = 33;
	private static final Gson GSON = new Gson();
	/** A bank in Varrock, far from both stops: travel is needed. */
	private static final WorldPoint VARROCK_BANK = new WorldPoint(3185, 3436, 0);

	/** The account capability the travel stack plans with (a fixture, settable as the test goes). */
	static final class FixedAccount extends AccountCapabilityAdapter
	{
		volatile AccountCapability cap;

		FixedAccount(net.runelite.api.Client client)
		{
			super(client, null);
		}

		@Override
		public AccountCapability capability()
		{
			return cap;
		}
	}

	/** The plugin's wiring, minus RuneLite's toolbar and overlay manager. */
	static final class Stack implements SpyglassFarmingPanel.Host
	{
		final RunHarness h;
		final FixedAccount account;
		final TravelCapabilityWiring travel;
		final FarmingPlanWorker planner = new FarmingPlanWorker();
		final FarmingPreferenceStore prefs = new FarmingPreferenceStore(null, GSON);
		final FarmingPreparationService preparation;
		final PohObserver house;
		final NavigationRuntime navigation = new NavigationRuntime();
		final GuidanceService guidance;
		String selection;

		Stack(RunHarness h, AccountCapability cap) throws Exception
		{
			this.h = h;
			account = new FixedAccount(h.fake.client);
			account.cap = cap;
			travel = new TravelCapabilityWiring(h.fake.client, GSON, account, h.adapter());
			travel.start();
			assertNotNull("transport catalogue loaded", travel.catalog());
			planner.setDirect(true);
			preparation = new FarmingPreparationService(h.fake.client, account, h.adapter(), travel, prefs, planner);
			house = new PohObserver(h.fake.client, null, GSON);
			travel.attachHouse(house::knowledge);
			guidance = new GuidanceService(h.fake.client, account, h.adapter(), travel, prefs, null, null, navigation, planner, house);
			navigation.start();
		}

		/** One game tick for every service, as RuneLite's event bus delivers it. */
		void tick()
		{
			h.settle(1);
			preparation.onGameTick(new GameTick());
			guidance.onGameTick(new GameTick());
		}

		/** Ticks until the guidance holds a walking path (the live route search runs off thread), at most ~20 s. */
		GuidanceModel tickUntilPath() throws InterruptedException
		{
			for (int i = 0; i < 400; i++)
			{
				tick();
				if (!guidance.model().getPath().isEmpty())
				{
					return guidance.model();
				}
				Thread.sleep(50);
			}
			return guidance.model();
		}

		@Override
		public FarmingSnapshot snapshot()
		{
			return h.adapter().uiSnapshot();
		}

		@Override
		public FarmingAccountFacts facts()
		{
			return travel.farmingFacts();
		}

		@Override
		public FarmingRunControls controls()
		{
			return h.adapter();
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
			return prefs.get();
		}

		@Override
		public void savePreferences(FarmingPreferences p)
		{
			prefs.set(p);
		}

		@Override
		public String selection()
		{
			return selection;
		}

		@Override
		public void saveSelection(String s)
		{
			selection = s;
		}

		@Override
		public void moveStop(int index, int delta)
		{
			FarmingPlanWorker.Result plan = planner.latest();
			if (plan != null)
			{
				h.adapter().setRunSuggestedOrder(FarmingPreparation.moved(plan.getPlan().getSites(), index, delta));
				tick(); // the plan follows at once (direct planning in this test)
			}
		}

		@Override
		public List<String> houseSetup()
		{
			return PohKnowledge.setupLines(house.knowledge(), h.now());
		}

		@Override
		public long now()
		{
			return h.now();
		}

		void stop()
		{
			navigation.stop();
			planner.stop();
		}
	}

	// -- sidebar helpers --------------------------------------------------------------------------------------------------

	static <T> List<T> all(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c) && c.isVisible())
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container && c.isVisible())
			{
				out.addAll(all((Container) c, type));
			}
		}
		return out;
	}

	/** Every visible label's text and every control's accessible name, in order. */
	static String texts(Container root)
	{
		StringBuilder b = new StringBuilder();
		for (Component c : all(root, Component.class))
		{
			if (c instanceof JLabel)
			{
				if (System.getenv("DBG") != null) b.append(c.getBounds()).append(c.getParent().getBounds()).append(' ');
				b.append(((JLabel) c).getText().replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim()).append('\n');
			}
			else if (c instanceof FarmingView.Button)
			{
				b.append('[').append(c.getAccessibleContext() == null ? c.getName() : c.getAccessibleContext().getAccessibleName()).append("]\n");
			}
		}
		return b.toString();
	}

	static FarmingView.Button link(Container root, String name)
	{
		for (FarmingView.Button l : all(root, FarmingView.Button.class))
		{
			if (name.equals(l.getAccessibleContext().getAccessibleName()))
			{
				return l;
			}
		}
		throw new AssertionError("no '" + name + "' in:\n" + texts(root));
	}

	static void pressButtonStartingWith(Container root, String prefix)
	{
		for (FarmingView.Button b : all(root, FarmingView.Button.class))
		{
			String n = b.getAccessibleContext().getAccessibleName();
			if (n != null && n.startsWith(prefix))
			{
				b.press();
				return;
			}
		}
		throw new AssertionError("no button '" + prefix + "…' in:\n" + texts(root));
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				layout(k);
			}
		}
	}

	/** The sidebar at its real width, tall enough to hold the whole page (no scrolling), drawn to build/screens. */
	static String shot(SpyglassFarmingPanel panel, String name) throws Exception
	{
		panel.refresh();
		int w = PluginPanel.PANEL_WIDTH;
		Component header = ((java.awt.BorderLayout) panel.getLayout()).getLayoutComponent(java.awt.BorderLayout.NORTH);
		FarmingView view = panel.view();
		for (int i = 0; i < 3; i++)
		{
			view.setSize(w - 2 * PluginPanel.BORDER_OFFSET, 1);
			layout(view);
			int h = header.getPreferredSize().height + view.getPreferredSize().height + PluginPanel.BORDER_OFFSET + 4;
			panel.setSize(w, Math.min(6000, h));
			layout(panel);
		}
		BufferedImage img = new BufferedImage(w * 2, panel.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.scale(2, 2);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		panel.paint(g);
		g.dispose();
		File dir = new File("build/screens");
		dir.mkdirs();
		ImageIO.write(img, "png", new File(dir, name + ".png"));
		String text = texts(panel);
		java.nio.file.Files.write(new File(dir, name + ".txt").toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return text;
	}

	static <T> T onEdt(Supplier<T> s) throws Exception
	{
		Object[] out = new Object[1];
		RuntimeException[] failure = new RuntimeException[1];
		Error[] error = new Error[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				out[0] = s.get();
			}
			catch (RuntimeException e)
			{
				failure[0] = e;
			}
			catch (Error e)
			{
				error[0] = e;
			}
		});
		if (error[0] != null)
		{
			throw error[0];
		}
		if (failure[0] != null)
		{
			throw failure[0];
		}
		@SuppressWarnings("unchecked")
		T t = (T) out[0];
		return t;
	}

	interface Shot
	{
		String take() throws Exception;
	}

	static String shoot(Shot s) throws Exception
	{
		return onEdt(() ->
		{
			try
			{
				return s.take();
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
		});
	}

	/**
	 * The guidance path as the scene overlay draws it (the guidance blue), on a tile grid around it: the player (white), the
	 * path's tiles, and the stop's patch objects (green). Not the game's rendering: a picture of the tiles the overlay paints.
	 */
	static void drawPath(String name, List<WorldPoint> path, WorldPoint player, List<int[]> patches) throws Exception
	{
		int minX = player.getX();
		int maxX = player.getX();
		int minY = player.getY();
		int maxY = player.getY();
		for (WorldPoint p : path)
		{
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minY = Math.min(minY, p.getY());
			maxY = Math.max(maxY, p.getY());
		}
		minX -= 6;
		minY -= 6;
		maxX += 6;
		maxY += 6;
		int cell = 8;
		BufferedImage img = new BufferedImage((maxX - minX + 1) * cell, (maxY - minY + 1) * cell, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new java.awt.Color(48, 44, 38));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setColor(new java.awt.Color(62, 58, 50));
		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				g.drawRect((x - minX) * cell, (maxY - y) * cell, cell, cell);
			}
		}
		g.setColor(new java.awt.Color(80, 170, 60));
		for (int[] b : patches)
		{
			if (b[4] >= minX && b[2] <= maxX && b[5] >= minY && b[3] <= maxY)
			{
				g.fillRect((b[2] - minX) * cell, (maxY - b[5]) * cell, (b[4] - b[2] + 1) * cell, (b[5] - b[3] + 1) * cell);
			}
		}
		g.setColor(new java.awt.Color(0x1E, 0xB4, 0xFF)); // GuidanceOverlays.PATH
		for (WorldPoint p : path)
		{
			g.fillRect((p.getX() - minX) * cell + 1, (maxY - p.getY()) * cell + 1, cell - 2, cell - 2);
		}
		g.setColor(java.awt.Color.WHITE);
		g.fillOval((player.getX() - minX) * cell, (maxY - player.getY()) * cell, cell, cell);
		g.dispose();
		File dir = new File("build/screens");
		dir.mkdirs();
		ImageIO.write(img, "png", new File(dir, name + ".png"));
	}

	// -- the world ---------------------------------------------------------------------------------------------------------

	/** The run's patch objects where the stops' markers are (a 2x2 herb patch, a 3x3 tree patch), so arrival can be seen. */
	private static void placePatches(RunHarness h)
	{
		FarmingPatchDef herb = h.catalog.patch("herb.falador");
		FarmingPatchDef tree = h.catalog.patch("tree.falador");
		FarmingSites.Tile ht = FarmingSites.tileOf(h.catalog, "herb.falador");
		FarmingSites.Tile tt = FarmingSites.tileOf(h.catalog, "tree.falador");
		h.fake.patchObjects.add(new int[]{90001, herb.getVarbit(), ht.x + 3, ht.y + 1, ht.x + 4, ht.y + 2, 0});
		h.fake.patchObjects.add(new int[]{90002, tree.getVarbit(), tt.x + 2, tt.y + 2, tt.x + 4, tt.y + 4, 0});
	}

	private static Map<Integer, Integer> herb(int value)
	{
		return Map.of(A, 3, B, 3, C, 3, D, value);
	}

	/** Maxed, standard spellbook, nothing carried: every teleport's items and runes have to be brought. */
	static AccountCapability account()
	{
		return account(AccountReaders.Spellbook.STANDARD);
	}

	/** The bank was seen an hour ago with runes in it (law, air, water, earth, fire) and {@code more}; nothing is carried. */
	static AccountCapability account(AccountReaders.Spellbook book, int... more)
	{
		int[] runes = {563, 200, 556, 2000, 555, 2000, 557, 2000, 554, 2000};
		int[] all = java.util.Arrays.copyOf(runes, runes.length + more.length);
		System.arraycopy(more, 0, all, runes.length, more.length);
		return AccountFixtures.spellbook(AccountFixtures.maxed(), book).bank(AccountFixtures.bank(3_600_000L, all)).build();
	}

	/** Preparation's rows as the sidebar shows them: supplies (label, status) and stops (site, transport, status). */
	static String rows(Stack s)
	{
		StringBuilder b = new StringBuilder();
		for (FarmingPreparationView.NeedRow r : s.preparation().getNeeds())
		{
			b.append(r.getLabel()).append(" · ").append(r.getStatus()).append('\n');
		}
		for (FarmingPreparationView.SiteRow r : s.preparation().getSites())
		{
			b.append(r.getOrder()).append(" ").append(r.getSite()).append(" · ").append(r.getTransport()).append(" · ").append(r.getStatus()).append('\n');
		}
		return b.toString();
	}

	@Test
	public void standardText() throws Exception
	{
		flow(1f, "standard");
	}

	@Test
	public void extraLargeText() throws Exception
	{
		flow(1.5f, "extra-large");
	}

	private static void flow(float scale, String size) throws Exception
	{
		try (RunHarness h = new RunHarness())
		{
			h.start();
			placePatches(h);
			h.see(TREE_SITE, Map.of(A, TREE_RIPE));
			h.see(HERB_SITE, herb(HERB_RIPE));
			Stack s = new Stack(h, account());
			SpyglassFarmingPanel panel = onEdt(() -> new SpyglassFarmingPanel(null, s));
			try
			{
				onEdt(() ->
				{
					panel.rebuild(scale);
					return null;
				});
				steps(h, s, panel, size);
			}
			finally
			{
				panel.stop();
				s.stop();
				onEdt(() ->
				{
					panel.rebuild(1f);
					return null;
				});
			}
		}
	}

	private static void steps(RunHarness h, Stack s, SpyglassFarmingPanel panel, String size) throws Exception
	{
		FarmingView view = panel.view();
		// 1. Combine herb and tree (tree clicked first), Falador only, supercompost on both; Start.
		onEdt(() ->
		{
			view.include(FarmingRunType.TREE, true);
			view.include(FarmingRunType.HERB, true);
			FarmingModel m = FarmingModel.build(h.catalog, s.snapshot(), s.now(), s.facts());
			for (FarmingRunType type : List.of(FarmingRunType.HERB, FarmingRunType.TREE))
			{
				for (FarmingModel.Row r : m.rows(type))
				{
					view.draft().setIncluded(r.getPatchId(), r.getPatchId().endsWith(".falador"));
				}
			}
			view.draft().setTypePlan(FarmingRunType.HERB, "RANARR");
			view.draft().setTypePlan(FarmingRunType.TREE, "MAPLE");
			view.chooseCompost(FarmingPatchType.HERB, FarmingCompost.SUPERCOMPOST);
			view.chooseCompost(FarmingPatchType.TREE, FarmingCompost.SUPERCOMPOST);
			view.invalidateStructure();
			return null;
		});
		String selection = shoot(() -> shot(panel, "1-combined-selection-" + size));
		// Start combined run at the top (under the run summary, above the patches) and at the bottom: one action, one state.
		onEdt(() ->
		{
			FarmingView.Button top = view.topStartButton();
			FarmingView.Button bottom = view.startButton();
			assertNotNull(top);
			assertEquals("Start combined run", top.getText());
			assertEquals(bottom.getText(), top.getText());
			assertTrue(top.isEnabled() && bottom.isEnabled());
			assertEquals("full width", view.getWidth(), top.getWidth());
			assertEquals("full width", view.getWidth(), bottom.getWidth());
			int summary = -1;
			int firstPatch = Integer.MAX_VALUE;
			for (java.awt.Component c : view.getComponents())
			{
				if (c instanceof JLabel && ((JLabel) c).getText().contains(" of ") && ((JLabel) c).getText().contains("patches"))
				{
					summary = c.getY();
				}
				if (c instanceof javax.swing.JPanel && all((Container) c, FarmingView.Button.class).stream()
					.anyMatch(b -> String.valueOf(b.getAccessibleContext().getAccessibleName()).startsWith("Include ")))
				{
					firstPatch = Math.min(firstPatch, c.getY());
				}
			}
			assertTrue("below the run summary", top.getY() > summary && summary >= 0);
			assertTrue("above the patch list", top.getY() < firstPatch);
			assertTrue("the bottom one stays at the bottom", bottom.getY() > firstPatch);
			return null;
		});
		assertTrue(selection, selection.indexOf("[Start combined run]") < selection.indexOf("HERB"));
		h.fake.location = VARROCK_BANK;
		boolean useTop = size.equals("standard");
		FarmingView.Button pressed = useTop ? view.topStartButton() : view.startButton();
		FarmingView.Button other = useTop ? view.startButton() : view.topStartButton();
		onEdt(() ->
		{
			pressed.press();
			return null;
		});
		FarmingRunIntent run = s.snapshot().getRun();
		assertNotNull("the run started", run);
		assertTrue(run.isPreparing());
		assertEquals("the selected patches", new java.util.TreeSet<>(List.of("herb.falador", "tree.falador")),
			new java.util.TreeSet<>(run.getIncludedPatchIds()));
		// The other button, pressed too (as it was drawn before the run began): still one run and one preparation.
		onEdt(() ->
		{
			other.press();
			return null;
		});
		assertEquals("one run", run.getIntentId(), s.snapshot().getRun().getIntentId());
		String prepScreen = shoot(() -> shot(panel, "1b-after-start-" + size));
		assertEquals("one Preparation screen", 1, prepScreen.split("PREPARING", -1).length - 1);

		// 2. Preparation: the plan (journeys and supplies) for this run; the stops get their quickest order once.
		s.tick();
		FarmingPreparationView prep = s.preparation();
		assertNotNull("preparation's plan is ready", prep);
		assertFalse("the run's order is set from the first plan", s.snapshot().getRun().getSuggestedOrder().isEmpty());
		String prepText = shoot(() -> shot(panel, "2-preparation-" + size));
		System.out.println("PREP\n" + prepText);
		List<FarmingPreparation.SitePlan> sites = s.planner.latest().getPlan().getSites();
		assertEquals(2, sites.size());
		for (FarmingPreparation.SitePlan sp : sites)
		{
			assertTrue("a journey for " + sp.getSite().getName(), sp.getTransport() != null || sp.getWalkFrom() != null);
		}
		assertNotNull("the first stop is teleported to", sites.get(0).getTransport());
		// The first stop's teleport is on the same checklist as the seeds, tools and compost.
		FarmingPreparation.SitePlan first = sites.get(0);
		String firstTeleport = FarmingPreparation.itemOrName(first.getTransport().getTransport());
		System.out.println("FIRST " + first.getSite().getName() + " via " + firstTeleport + " keys " + first.getNeedKeys());
		assertTrue(prepText, prepText.contains("Herb seeds"));
		assertTrue(prepText, prepText.contains("Seed dibber"));
		assertTrue(prepText, prepText.contains("Supercompost"));
		boolean travelOnChecklist = false;
		for (FarmingPreparationView.NeedRow r : prep.getNeeds())
		{
			travelOnChecklist |= first.getNeedKeys().contains(r.getKey());
		}
		assertTrue("teleport items or runes on the checklist:\n" + prepText, travelOnChecklist);

		// 3. Reorder with the arrows: the plan, its journeys and the order follow.
		String secondName = sites.get(1).getSite().getName();
		onEdt(() ->
		{
			link(panel, "Move " + secondName + " earlier").press();
			return null;
		});
		List<FarmingPreparation.SitePlan> reordered = s.planner.latest().getPlan().getSites();
		assertEquals(secondName, reordered.get(0).getSite().getName());
		String prep2 = shoot(() -> shot(panel, "3-preparation-reordered-" + size));
		assertTrue(prep2, prep2.indexOf(secondName) < prep2.indexOf(first.getSite().getName()));
		FarmingPreparation.SitePlan stop1 = reordered.get(0);
		assertNotNull("the first stop in the new order is teleported to", stop1.getTransport());
		String stop1Teleport = FarmingPreparation.itemOrName(stop1.getTransport().getTransport());

		// House setup: offered in preparation, saying what is confirmed (nothing yet).
		onEdt(() ->
		{
			link(panel, "House teleports setup, optional").press();
			return null;
		});
		String house = shoot(() -> shot(panel, "3b-preparation-house-setup-" + size));
		assertTrue(house, house.contains("isn't confirmed yet"));

		// 4. Begin: NEXT names the same teleport preparation shows for the first stop in the player's order.
		onEdt(() ->
		{
			pressButtonStartingWith(panel, s.preparation().getBeginLabel());
			return null;
		});
		assertFalse(s.snapshot().getRun().isPreparing());
		s.tick();
		GuidanceModel g = s.guidance.model();
		System.out.println("NEXT " + g.getKind() + " | " + g.getTitle() + " | " + g.getPlayerLabel() + " | " + g.getGuide() + " | " + g.getLines());
		assertEquals("TRAVEL", g.getKind());
		assertTrue(g.getTitle(), g.getTitle().contains(stop1.getSite().getName().replaceFirst(" \\u00b7.*", "")));
		String active = shoot(() -> shot(panel, "4-active-teleport-" + size));
		System.out.println("ACTIVE\n" + active);
		assertTrue("NEXT and preparation agree on " + stop1Teleport + ":\n" + g.getLines(), String.join("\n", g.getLines()).contains(stop1Teleport));

		// 5. Arrive by that teleport: a blue walking path to the patch.
		FarmingSites.Tile arrival = stop1.getTransport().getTransport().arrival == null ? null
			: new FarmingSites.Tile(stop1.getTransport().getTransport().arrival.x, stop1.getTransport().getTransport().arrival.y,
			stop1.getTransport().getTransport().arrival.plane);
		assertNotNull("the teleport's arrival", arrival);
		h.fake.location = new WorldPoint(arrival.x, arrival.y, arrival.plane);
		g = s.tickUntilPath();
		System.out.println("WALK " + g.getKind() + " | " + g.getPlayerLabel() + " | " + g.getGuide() + " | path " + g.getPath().size());
		assertFalse("a blue walking path from the teleport's arrival", g.getPath().isEmpty());
		assertEquals("still the first stop in the player's order", stop1.getSite().getName(), g.getTitle());
		WorldPoint goal = g.getTarget();
		assertTrue("the path heads for the patch", g.getPath().get(g.getPath().size() - 1).distanceTo2D(goal)
			< h.fake.location.distanceTo2D(goal));
		shoot(() -> shot(panel, "5-active-walking-" + size));
		if (size.equals("standard"))
		{
			drawPath("path-1-after-teleport", g.getPath(), h.fake.location, h.fake.patchObjects);
		}

		// 6. Detour: the player wanders off; the path is planned again from where they stand.
		WorldPoint off = new WorldPoint(h.fake.location.getX() - 15, h.fake.location.getY() - 20, 0);
		h.fake.location = off;
		GuidanceModel before = g;
		g = s.tickUntilPath();
		for (int i = 0; i < 20 && (g.getPath().isEmpty() || g.getPath().get(0).distanceTo2D(off) > 3); i++)
		{
			g = s.tickUntilPath();
		}
		System.out.println("DETOUR path " + g.getPath().size() + " from " + (g.getPath().isEmpty() ? null : g.getPath().get(0)));
		assertFalse("still guided after the detour", g.getPath().isEmpty());
		assertTrue("the new path starts where the player is", g.getPath().get(0).distanceTo2D(off) <= 3);
		assertEquals(stop1.getSite().getName(), g.getTitle());
		assertNotEquals(before.getPath(), g.getPath());
		if (size.equals("standard"))
		{
			drawPath("path-2-after-detour", g.getPath(), off, h.fake.patchObjects);
		}

		// 7. Arrive beside the patch: the patch action and its highlight.
		FarmingPatchDef def = h.catalog.patch(stop1.getSite().getPatchIds().get(0));
		int[] obj = null;
		for (int[] o : h.fake.patchObjects)
		{
			obj = o[1] == def.getVarbit() ? o : obj;
		}
		h.fake.location = new WorldPoint(obj[2] - 1, obj[3], 0);
		for (int i = 0; i < 4; i++)
		{
			s.tick();
		}
		g = s.guidance.model();
		System.out.println("ARRIVED " + g.getKind() + " | " + g.getPlayerLabel() + " | " + g.getGuide() + " | footprints " + g.getFootprints().size());
		assertEquals("ACT", g.getKind());
		assertFalse("the patch is outlined", g.getFootprints().isEmpty());
		String arrived = shoot(() -> shot(panel, "6-active-at-patch-" + size));
		System.out.println("AT PATCH\n" + arrived);
	}

	/** A teleport that stops being usable: preparation and NEXT both move to another way, and say so. */
	@Test
	public void unavailableTeleport_preparationAndNextMoveTogether() throws Exception
	{
		try (RunHarness h = new RunHarness())
		{
			h.start();
			placePatches(h);
			h.see(TREE_SITE, Map.of(A, TREE_RIPE));
			h.see(HERB_SITE, herb(HERB_RIPE));
			Stack s = new Stack(h, account());
			try
			{
				FarmingModel m = FarmingModel.build(h.catalog, s.snapshot(), s.now(), s.facts());
				com.spyglassfarming.farming.FarmingPlanDraft draft = new com.spyglassfarming.farming.FarmingPlanDraft();
				for (FarmingModel.Row r : m.rows(FarmingRunType.HERB))
				{
					draft.setIncluded(r.getPatchId(), r.getPatchId().equals("herb.falador"));
				}
				h.fake.location = VARROCK_BANK;
				assertTrue(h.adapter().startRun(com.spyglassfarming.farming.FarmingRunRequest.from(m, FarmingRunType.HERB, draft)).isStarted());
				s.tick();
				// Falador Teleport (the spell, runes in the bank) is the way there; Falador teleport tablets are in the bank too.
				FarmingPreparation.SitePlan before = s.planner.latest().getPlan().getSites().get(0);
				String was = FarmingPreparation.itemOrName(before.getTransport().getTransport());
				String prepBefore = rows(s);
				System.out.println("BEFORE " + was + "\n" + prepBefore);
				// The spellbook changes to Ancient: the standard spell is no longer usable. Preparation moves to the tablet.
				s.account.cap = account(AccountReaders.Spellbook.ANCIENT, 8009, 5);
				s.tick();
				FarmingPreparation.SitePlan after = s.planner.latest().getPlan().getSites().get(0);
				assertNotNull("another way is found", after.getTransport());
				String now = FarmingPreparation.itemOrName(after.getTransport().getTransport());
				String prepAfter = rows(s);
				System.out.println("AFTER " + now + "\n" + prepAfter);
				assertNotEquals(was, now);
				assertTrue(prepAfter, prepAfter.contains(now));
				h.adapter().closeRunPreparation();
				s.tick();
				GuidanceModel g = s.guidance.model();
				System.out.println("NEXT " + g.getKind() + " | " + g.getPlayerLabel() + " | " + g.getGuide() + " | " + g.getLines());
				assertTrue("NEXT names the same way preparation shows", String.join("\n", g.getLines()).contains(now));
				// Nothing usable at all: the stop says so, and guidance lets the player travel their own way.
				s.account.cap = account(AccountReaders.Spellbook.ANCIENT);
				s.tick();
				FarmingPreparation.SitePlan none = s.planner.latest().getPlan().getSites().get(0);
				g = s.guidance.model();
				System.out.println("NONE " + none.getNote() + " | NEXT " + g.getKind() + " | " + g.getPlayerLabel() + " | " + g.getLines());
				assertEquals(null, none.getTransport());
				assertEquals("TRAVEL", g.getKind());
				assertTrue(g.getLines().toString(), String.join("\n", g.getLines()).contains("Choose your own travel"));
			}
			finally
			{
				s.stop();
			}
		}
	}

	/**
	 * The combined herb + tree run with supercompost, guided at each patch from start to completion: the tree first (the
	 * player's order), every step's action from the real guidance (clear, stump, plant, compost; harvest, plant, compost), the
	 * game's compost messages attributed to the patches, and the run completes.
	 */
	@Test
	public void combinedHerbAndTreeWithCompost_guidedToCompletion() throws Exception
	{
		int herbRipe = com.spyglassfarming.farming.FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, com.spyglassfarming.farming.FarmingProduce.RANARR,
			com.spyglassfarming.farming.PatchCondition.HARVESTABLE, -1);
		int herbPlanted = com.spyglassfarming.farming.FarmingKnowledgeFixtures.raw(FarmingPatchType.HERB, com.spyglassfarming.farming.FarmingProduce.RANARR,
			com.spyglassfarming.farming.PatchCondition.GROWING, 0);
		int treeRipe = com.spyglassfarming.farming.FarmingKnowledgeFixtures.raw(FarmingPatchType.TREE, com.spyglassfarming.farming.FarmingProduce.MAPLE,
			com.spyglassfarming.farming.PatchCondition.HARVESTABLE, -1);
		int treePlanted = com.spyglassfarming.farming.FarmingKnowledgeFixtures.raw(FarmingPatchType.TREE, com.spyglassfarming.farming.FarmingProduce.MAPLE,
			com.spyglassfarming.farming.PatchCondition.GROWING, 0);
		try (RunHarness h = new RunHarness())
		{
			h.start();
			placePatches(h);
			h.see(HERB_SITE, herb(herbRipe));
			h.see(TREE_SITE, Map.of(A, treeRipe));
			Stack s = new Stack(h, account());
			try
			{
				FarmingModel m = FarmingModel.build(h.catalog, s.snapshot(), s.now(), s.facts());
				com.spyglassfarming.farming.FarmingPlanDraft draft = new com.spyglassfarming.farming.FarmingPlanDraft();
				for (FarmingRunType type : List.of(FarmingRunType.HERB, FarmingRunType.TREE))
				{
					for (FarmingModel.Row r : m.rows(type))
					{
						draft.setIncluded(r.getPatchId(), r.getPatchId().endsWith(".falador"));
					}
					draft.setCompost(type, FarmingCompost.SUPERCOMPOST);
				}
				draft.setTypePlan(FarmingRunType.HERB, "RANARR");
				draft.setTypePlan(FarmingRunType.TREE, "MAPLE");
				h.fake.location = VARROCK_BANK;
				assertTrue(h.adapter().startRun(com.spyglassfarming.farming.FarmingRunRequest.from(m, List.of(FarmingRunType.HERB, FarmingRunType.TREE), draft)
					.withExtras(FarmingPreferences.defaults())).isStarted());
				s.tick();
				// The player puts the tree first.
				List<FarmingPreparation.SitePlan> sites = s.planner.latest().getPlan().getSites();
				if (!sites.get(0).getSite().getPatchIds().contains("tree.falador"))
				{
					s.moveStop(1, -1);
				}
				assertTrue(s.planner.latest().getPlan().getSites().get(0).getSite().getPatchIds().contains("tree.falador"));
				h.adapter().closeRunPreparation();
				List<String> steps = new ArrayList<>();
				int[] treeObj = h.fake.patchObjects.get(1);
				int[] herbObj = h.fake.patchObjects.get(0);
				WorldPoint besideTree = new WorldPoint(treeObj[2] - 1, treeObj[3], 0);
				WorldPoint besideHerb = new WorldPoint(herbObj[2] - 1, herbObj[3], 0);
				steps.add(step(s, h, besideTree, Map.of(A, treeRipe)));
				steps.add(step(s, h, besideTree, Map.of(A, 34)));
				steps.add(step(s, h, besideTree, Map.of(A, 3)));
				steps.add(step(s, h, besideTree, Map.of(A, treePlanted)));
				assertTrue(h.compost("tree.falador", "You treat the tree patch with supercompost."));
				steps.add(step(s, h, besideTree, Map.of(A, treePlanted)));
				// A nearby walking leg: the herb patch is walked to from the tree patch (no teleport to bring for it), and NEXT
				// draws the walk from where the player stands.
				FarmingPreparation.SitePlan herbStop = s.planner.latest().getPlan().getSites().get(1);
				System.out.println("HERB STOP " + herbStop.getWalkFrom() + " " + herbStop.getWalkTiles() + " " + herbStop.getTransport());
				assertEquals("Falador · tree", herbStop.getWalkFrom());
				assertNull("nothing to bring for a walked stop", herbStop.getTransport());
				GuidanceModel walking = s.tickUntilPath();
				System.out.println("WALK LEG " + walking.getKind() + " | " + walking.getGuide() + " | " + walking.getLines() + " | path " + walking.getPath().size());
				assertEquals("TRAVEL", walking.getKind());
				assertFalse("a blue path from the tree patch to the herbs", walking.getPath().isEmpty());
				assertTrue(walking.getPath().get(0).distanceTo2D(besideTree) <= 3);
				steps.add(step(s, h, besideHerb, herb(herbRipe)));
				steps.add(step(s, h, besideHerb, herb(3)));
				steps.add(step(s, h, besideHerb, herb(herbPlanted)));
				assertTrue(h.compost("herb.falador", "You treat the herb patch with supercompost."));
				steps.add(step(s, h, besideHerb, herb(herbPlanted)));
				System.out.println("STEPS\n" + String.join("\n", steps));
				assertTrue(steps.get(0), steps.get(0).startsWith("ACT Falador · tree"));
				assertTrue(steps.get(3), steps.get(3).toLowerCase().contains("compost"));
				assertTrue("after the tree is composted, the herbs: " + steps.get(4), steps.get(4).contains("Falador · herb"));
				assertTrue(steps.get(5), steps.get(5).startsWith("ACT Falador · herb"));
				assertTrue(steps.get(7), steps.get(7).toLowerCase().contains("compost"));
				assertEquals("every patch done and composted: the run is complete", FarmingRunIntent.State.COMPLETE,
					s.snapshot().getRun().getState());
				assertFalse("nothing left to guide", s.guidance.model().isActive());
			}
			finally
			{
				s.stop();
			}
		}
	}

	/** The player stands at {@code at} and sees these patch values; NEXT as "KIND stop | action". */
	private static String step(Stack s, RunHarness h, WorldPoint at, Map<Integer, Integer> varbits)
	{
		h.see(at, varbits);
		for (int i = 0; i < 4; i++)
		{
			s.tick();
		}
		GuidanceModel g = s.guidance.model();
		return g.getKind() + " " + g.getTitle() + " | " + g.getPlayerLabel() + " | " + g.getGuide() + " | footprints " + g.getFootprints().size();
	}
}
