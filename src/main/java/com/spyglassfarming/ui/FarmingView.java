package com.spyglassfarming.ui;

import com.spyglassfarming.farming.CompostBin;
import com.spyglassfarming.farming.FarmingCompost;
import com.spyglassfarming.farming.FarmingModel;
import com.spyglassfarming.farming.FarmingPatchType;
import com.spyglassfarming.farming.FarmingPlanDraft;
import com.spyglassfarming.farming.FarmingPreferences;
import com.spyglassfarming.farming.FarmingPreparation;
import com.spyglassfarming.farming.FarmingPreparationView;
import com.spyglassfarming.farming.FarmingRunControls;
import com.spyglassfarming.farming.FarmingRunEvaluator;
import com.spyglassfarming.farming.FarmingRunIntent;
import com.spyglassfarming.farming.FarmingRunRequest;
import com.spyglassfarming.farming.FarmingRunStore;
import com.spyglassfarming.farming.FarmingRunType;
import com.spyglassfarming.farming.FarmingRunView;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.farming.FarmingText;
import com.spyglassfarming.farming.RunExtras;
import com.spyglassfarming.guidance.GuidanceModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * The Farming sidebar: run-type selection with its patches, preparation, the run and settings, all drawn from the immutable
 * models. Each refresh draws the page again only when what it shows has changed (its text and states), so the 1 s poll
 * leaves an unchanged page, its scroll position and its focus alone.
 */
final class FarmingView extends JPanel
{
	static final int WIDTH = 165;

	private final ItemManager items;
	private final SpyglassFarmingPanel.Host host;
	private final Runnable refresh;
	private final FarmingPlanDraft draft = new FarmingPlanDraft();
	private final Set<FarmingRunType> selected = EnumSet.noneOf(FarmingRunType.class);
	private boolean more;
	private boolean lockedOpen;
	private boolean houseOpen;
	private boolean settings;
	private boolean endConfirm;
	private boolean loaded;
	private Object owner;
	private List<String> problems = Collections.emptyList();
	private FarmingModel model;
	private StringBuilder sig = new StringBuilder();
	private String shown;
	private Button startButton;
	private Button topStartButton;
	/** Whether Start may be pressed now (both Start buttons share it), and a run was just started (no second one). */
	private boolean startOk;
	private boolean started;

	FarmingView(ItemManager items, SpyglassFarmingPanel.Host host, Runnable refresh)
	{
		this.items = items;
		this.host = host;
		this.refresh = refresh;
		setOpaque(false);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
	}

	// -- what tests and the panel drive -------------------------------------------------------------------------------

	FarmingPlanDraft draft()
	{
		return draft;
	}

	Button startButton()
	{
		return startButton;
	}

	Button topStartButton()
	{
		return topStartButton;
	}

	boolean isSettingsOpen()
	{
		return settings;
	}

	void setSettingsOpen(boolean open)
	{
		settings = open;
		redraw();
	}

	/** Puts a run type in the run being planned or takes it out. */
	void include(FarmingRunType type, boolean on)
	{
		if (on)
		{
			selected.add(type);
		}
		else
		{
			selected.remove(type);
		}
		StringBuilder s = new StringBuilder();
		for (FarmingRunType t : selected)
		{
			s.append(s.length() == 0 ? "" : ",").append(t.name());
		}
		host.saveSelection(s.toString());
		problems = Collections.emptyList();
		redraw();
	}

	/** A compost choice: saved per patch type and profile; an open run's pending work follows (preparation recalculates). */
	void chooseCompost(FarmingPatchType type, FarmingCompost choice)
	{
		host.savePreferences(prefs().withCompost(type, choice));
		if (model != null && model.getRun() != null && controls() != null)
		{
			controls().setRunCompost(type, choice);
		}
		refresh.run();
	}

	void invalidateStructure()
	{
		shown = null;
	}

	private void redraw()
	{
		shown = null;
		if (model != null)
		{
			render(model);
		}
	}

	private FarmingPreferences prefs()
	{
		FarmingPreferences p = host.preferences();
		return p == null ? FarmingPreferences.defaults() : p;
	}

	private FarmingRunControls controls()
	{
		return host.controls();
	}

	private void act(Runnable r)
	{
		r.run();
		refresh.run();
	}

	// -- drawing ------------------------------------------------------------------------------------------------------

	void render(FarmingModel m)
	{
		model = m;
		if (m.getOwner() != null && !m.getOwner().equals(owner))
		{
			if (owner != null)
			{
				draft.clear();
				selected.clear();
				endConfirm = false;
				problems = Collections.emptyList();
			}
			owner = m.getOwner();
			loaded = false;
		}
		if (!loaded && m.getState() == FarmingSnapshot.State.AVAILABLE)
		{
			loaded = true;
			draft.loadFamilies(prefs().isIncludeAllotments(), prefs().isIncludeFlowers());
			String saved = host.selection();
			for (String s : saved == null ? new String[0] : saved.split(","))
			{
				FarmingRunType t = FarmingRunType.parse(s);
				if (t != null)
				{
					selected.add(t);
				}
			}
		}
		if (m.getRun() == null)
		{
			endConfirm = false;
		}
		else
		{
			started = false;
		}
		sig = new StringBuilder();
		List<JComponent> page = new ArrayList<>();
		startButton = null;
		topStartButton = null;
		if (settings)
		{
			settings(page);
		}
		else if (m.getState() != FarmingSnapshot.State.AVAILABLE)
		{
			page.add(text(m.getState() == FarmingSnapshot.State.LOADING ? "Loading your patches…"
				: m.getState() == FarmingSnapshot.State.LOGGED_OUT ? "Log in to see your patches."
				: "Farming is tracked on standard worlds only. Nothing is recorded here.", SpyglassFonts.regular(), SpyglassTheme.TEXT_SECONDARY));
		}
		else if (m.getRun() == null)
		{
			overview(page, m);
		}
		else if (m.getRun().getPhase() == FarmingRunView.Phase.PREPARING)
		{
			preparation(page, m.getRun(), host.preparation());
		}
		else
		{
			run(page, m.getRun());
		}
		String now = sig.toString();
		if (now.equals(shown))
		{
			return;
		}
		shown = now;
		removeAll();
		for (JComponent c : page)
		{
			c.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(c);
			add(Box.createVerticalStrut(4));
		}
		revalidate();
		repaint();
	}

	// -- selection ----------------------------------------------------------------------------------------------------

	private void overview(List<JComponent> page, FarmingModel m)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 3, 3));
		grid.setOpaque(false);
		for (FarmingRunType t : FarmingRunType.values())
		{
			if (!t.isSpecial() || more || selected.contains(t))
			{
				boolean on = selected.contains(t);
				String[] st = status(m, t);
				Button b = new Button("<html><div style='width:" + (WIDTH / 2 - 12) + "px'><b>" + (on ? "✓ " : "") + t.getButtonLabel()
					+ "</b><br><font color='" + st[1] + "'>" + st[0] + "</font></div></html>", Style.TILE, () -> include(t, !on));
				b.selected(on);
				b.name(t.getTitle() + ": " + st[0] + (on ? ", in the run" : ""));
				grid.add(b);
			}
		}
		page.add(grid);
		page.add(button((more ? "Fewer types ▴" : "More types ▾"), Style.PLAIN, () ->
		{
			more = !more;
			redraw();
		}));
		if (selected.isEmpty())
		{
			page.add(text("Pick one or more run types to plan a run. Picking several makes one combined run.", SpyglassFonts.small(),
				SpyglassTheme.TEXT_SECONDARY));
			return;
		}
		List<FarmingModel.Row> rows = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (FarmingRunType t : selected)
		{
			names.add(t.getTitle());
			if (t.isCrop())
			{
				rows.addAll(m.rows(t));
			}
		}
		int[] scope = draft.scope(rows, m.getNow());
		int bins = selected.contains(FarmingRunType.COMPOST) ? prefs().getRunBins().size() : 0;
		page.add(text("In this run: " + String.join(" + ", names), SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
		page.add(text(scope[0] + " of " + scope[1] + " patches · " + scope[2] + (scope[2] == 1 ? " site" : " sites")
			+ (bins == 0 ? "" : " · " + bins + (bins == 1 ? " bin" : " bins")), SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		startOk = scope[0] > 0 || selected.size() == 1 && bins > 0;
		topStartButton = newStartButton(); // the same action and state as the one at the bottom
		page.add(topStartButton);
		boolean bounty = false;
		int locked = 0;
		for (FarmingRunType t : selected)
		{
			if (t == FarmingRunType.COMPOST)
			{
				bins(page, m);
				continue;
			}
			page.add(header(t.getTitle()));
			boolean harvestOnly = FarmingRunIntent.HARVEST_ONLY.equals(draft.getTypePlan(t));
			page.add(button("After harvest — " + (harvestOnly ? "Harvest only" : "Replant") + " ▸", Style.ROW, () ->
			{
				draft.setTypePlan(t, harvestOnly ? FarmingRunIntent.REPLANT_ANY : FarmingRunIntent.HARVEST_ONLY);
				redraw();
			}));
			compostChoices(page, compostTypes(t), compostTypes(t).size() > 1);
			if (t == FarmingRunType.ALLOTMENT)
			{
				page.add(row(family(FarmingPatchType.ALLOTMENT, "Allotments", draft.isIncludeAllotments(), m),
					family(FarmingPatchType.FLOWER, "Flowers", draft.isIncludeFlowers(), m), null));
			}
			for (FarmingModel.Row r : m.rows(t))
			{
				page.add(patchCard(r, m));
			}
			locked += m.excluded(t).size();
			bounty |= FarmingPreparation.bountyApplies(t);
		}
		if (locked > 0)
		{
			page.add(button((lockedOpen ? "▾ " : "▸ ") + "Locked / unverified patches (" + locked + ")", Style.LINK, () ->
			{
				lockedOpen = !lockedOpen;
				redraw();
			}));
			for (FarmingRunType t : selected)
			{
				for (FarmingModel.Excluded e : lockedOpen && t.isCrop() ? m.excluded(t) : Collections.<FarmingModel.Excluded>emptyList())
				{
					page.add(text(e.getRow().getLocation() + ": " + e.getReason(), SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
				}
			}
		}
		if (bounty)
		{
			page.add(button((draft.isUseBounty() ? "☑ " : "☐ ") + "Wear an amulet of bounty (allotments)", Style.LINK, () ->
			{
				draft.setUseBounty(!draft.isUseBounty());
				redraw();
			}));
		}
		startButton = newStartButton();
		page.add(startButton);
		page.add(text(startOk ? "One checklist and one suggested order. You do the run in game." : "Include at least one patch or bin to start.",
			SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
		for (String p : problems)
		{
			page.add(text(p, SpyglassFonts.small(), SpyglassTheme.FARM_EMBER));
		}
	}

	private Button family(FarmingPatchType type, String name, boolean on, FarmingModel m)
	{
		return button((on ? "☑ " : "☐ ") + name, Style.LINK, () ->
		{
			draft.setInclude(type, !on, m.rows(FarmingRunType.ALLOTMENT));
			host.savePreferences(type == FarmingPatchType.ALLOTMENT ? prefs().withIncludeAllotments(!on) : prefs().withIncludeFlowers(!on));
			redraw();
		});
	}

	/** A type's status on its button: ready, else growing, dead, diseased, clear. Colour as HTML. */
	private static String[] status(FarmingModel m, FarmingRunType t)
	{
		if (t == FarmingRunType.COMPOST)
		{
			return new String[]{"Compost bins", "#cabea2"};
		}
		FarmingModel.TypeSummary s = m.summary(t);
		return s.getReady() > 0 ? new String[]{s.getReady() + " Ready", "#70b96e"}
			: s.getGrowing() > 0 ? new String[]{s.getGrowing() + " Growing", "#e6d8b8"}
			: s.getDead() > 0 ? new String[]{s.getDead() + " Dead", "#e05a48"}
			: s.getAttention() > 0 ? new String[]{s.getAttention() + " Diseased", "#e8866a"}
			: s.getClear() > 0 ? new String[]{s.getClear() + " Clear", "#cabea2"}
			: new String[]{s.getTracked() == 0 ? "Not tracked" : "Check patch", "#b3a78e"};
	}

	private JComponent patchCard(FarmingModel.Row r, FarmingModel m)
	{
		boolean in = draft.isIncluded(r, m.getNow());
		JPanel tile = tile(in ? rail(r.getChipTone()) : null);
		Button box = button((in ? "☑ " : "☐ ") + r.getLocation(), Style.LINK, () ->
		{
			draft.setIncluded(r.getPatchId(), !in);
			redraw();
		});
		box.setFont(SpyglassFonts.bold());
		box.name("Include " + r.getLocation() + " in the plan");
		tile.add(box);
		String chip = r.getChip().startsWith("~") ? "Ready in " + r.getChip() : r.getChip();
		tile.add(text(chip, SpyglassFonts.regular(), color(r.getChipTone())));
		tile.add(text(r.getProvenance(), SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
		return tile;
	}

	private void bins(List<JComponent> page, FarmingModel m)
	{
		page.add(header("COMPOST BINS"));
		FarmingPreferences p = prefs();
		for (CompostBin.Def d : CompostBin.ALL)
		{
			boolean on = p.isRunBin(d.getBinId());
			CompostBin.Seen s = m.getBins().get(d.getBinId());
			CompostBin.Upgrade up = p.binUpgrade(d.getBinId());
			String state = "Not seen yet";
			CompostBin.Task task = null;
			if (s != null)
			{
				CompostBin.State st = s.state(d);
				state = st == null ? "Not recognised" : st.phase == CompostBin.Phase.ROTTED ? st.contents.getLabel() + " rotted" : CompostBin.describe(d, st);
				state += " · " + FarmingText.age(Math.max(0, m.getNow() - s.getAt())) + " ago";
				task = CompostBin.task(d, s, up, m.getNow());
			}
			JPanel tile = tile(on && task != null ? SpyglassTheme.ACCENT_GOLD : null);
			tile.add(button((on ? "☑ " : "☐ ") + d.getPlace(), Style.LINK, () -> act(() -> host.savePreferences(prefs().withRunBin(d.getBinId(), !on)))));
			tile.add(button("Make: " + (up == CompostBin.Upgrade.NONE ? "Rotted" : up == CompostBin.Upgrade.SUPER ? "Super" : "Ultra") + " ▸", Style.LINK,
				() -> act(() -> host.savePreferences(prefs().withBinUpgrade(d.getBinId(), CompostBin.Upgrade.values()[(up.ordinal() + 1)
					% CompostBin.Upgrade.values().length])))));
			tile.add(text(state, SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
			if (task != null)
			{
				tile.add(text("▸ " + task.label, SpyglassFonts.small(), on ? SpyglassTheme.ACCENT_GOLD : SpyglassTheme.TEXT_SECONDARY));
			}
			page.add(tile);
		}
	}

	/** One row per patch type; {@code named}: say which type (several share a heading, or none heads them). */
	private void compostChoices(List<JComponent> page, List<FarmingPatchType> types, boolean named)
	{
		for (FarmingPatchType t : types)
		{
			FarmingCompost c = prefs().compostFor(t);
			Button b = button("Compost" + (named ? " (" + t.getDisplayName().toLowerCase(Locale.ROOT) + ")" : "") + " — " + c.getLabel() + " ▸", Style.ROW,
				() -> chooseCompost(t, FarmingCompost.values()[(c.ordinal() + 1) % FarmingCompost.values().length]));
			b.setToolTipText("Click for the next choice: none, compost, supercompost, ultracompost, a bottomless bucket or Fertile Soil.");
			page.add(b);
		}
	}

	static List<FarmingPatchType> compostTypes(FarmingRunType t)
	{
		List<FarmingPatchType> out = new ArrayList<>();
		if (t != null && t.isCrop())
		{
			out.add(t.getPrimary());
			out.addAll(t.getCompanions());
		}
		return out;
	}

	private Button newStartButton()
	{
		Button b = button(selected.size() > 1 ? "Start combined run" : "Start run", Style.PRIMARY, this::start);
		b.setEnabled(startOk);
		sig.append(startOk);
		return b;
	}

	private void start()
	{
		if (model == null || selected.isEmpty() || !startOk || started || model.getRun() != null)
		{
			return; // nothing to start, or a run was just started (from either button): one run, one preparation
		}
		List<FarmingRunType> ordered = new ArrayList<>(selected);
		ordered.sort((a, b) -> Boolean.compare(!a.isCrop(), !b.isCrop())); // a run is named by its crops first
		FarmingRunStore.StartResult result = controls() == null ? null
			: controls().startRun(FarmingRunRequest.from(model, ordered, draft).withExtras(prefs()));
		problems = result == null ? Collections.singletonList("Farming isn't connected yet. Log in on a standard world.")
			: result.isStarted() ? Collections.emptyList() : new ArrayList<>(result.getProblems());
		started = result != null && result.isStarted();
		endConfirm = false;
		shown = null;
		refresh.run();
	}

	// -- preparation --------------------------------------------------------------------------------------------------

	private void preparation(List<JComponent> page, FarmingRunView run, FarmingPreparationView prep)
	{
		page.add(text(run.getTitle() + " · PREPARING", SpyglassFonts.bold(), SpyglassTheme.ACCENT_GOLD));
		if (prep == null)
		{
			page.add(text("Working out what to bring and the way to each stop…", SpyglassFonts.regular(), SpyglassTheme.TEXT_SECONDARY));
			endLink(page, run);
			return;
		}
		JPanel head = tile(prep.isReady() ? SpyglassTheme.ACCENT_SAGE : SpyglassTheme.ACCENT_GOLD);
		head.add(text(prep.getHeadline(), SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
		for (String s : prep.getSublines())
		{
			head.add(text(s, SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		}
		page.add(head);
		page.add(button(prep.getBeginLabel(), Style.PRIMARY, () -> act(() -> controls().closeRunPreparation())));
		if (prep.getSlots() != null)
		{
			page.add(text(prep.getSlots(), SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		}
		needs(page, "STILL NEEDED", prep.rows(FarmingPreparationView.Group.NEEDED));
		if (!prep.getSites().isEmpty())
		{
			page.add(header("YOUR ORDER"));
			List<FarmingPreparationView.SiteRow> sites = prep.getSites();
			for (int i = 0; i < sites.size(); i++)
			{
				page.add(siteCard(sites.get(i), i, sites.size()));
			}
		}
		List<String> house = host.houseSetup();
		if (house != null && !house.isEmpty())
		{
			Button b = button((houseOpen ? "▾ " : "▸ ") + "House teleports (optional setup)", Style.LINK, () ->
			{
				houseOpen = !houseOpen;
				redraw();
			});
			b.name("House teleports setup, optional");
			page.add(b);
			for (String l : houseOpen ? house : Collections.<String>emptyList())
			{
				page.add(text(l, SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
			}
		}
		needs(page, "OPTIONAL", prep.rows(FarmingPreparationView.Group.OPTIONAL));
		List<FarmingPatchType> types = new ArrayList<>();
		for (FarmingRunType t : run.getIntent().getRunTypes())
		{
			types.addAll(compostTypes(t));
		}
		if (!types.isEmpty())
		{
			page.add(header("COMPOST ON PATCHES"));
			compostChoices(page, types, true);
		}
		for (String n : prep.getNotes())
		{
			page.add(text(n, SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
		}
		endLink(page, run);
	}

	private void needs(List<JComponent> page, String title, List<FarmingPreparationView.NeedRow> rows)
	{
		if (rows.isEmpty())
		{
			return;
		}
		page.add(row(text(title, SpyglassFonts.smallBold(), SpyglassTheme.TILE_TEXT_QUIET, 0), null,
			text(String.valueOf(rows.size()), SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED, 0)));
		for (FarmingPreparationView.NeedRow r : rows)
		{
			JPanel tile = tile(null);
			boolean q = r.getQuantity() != null && !r.getQuantity().isEmpty();
			tile.add(row(icon(r.getItemId()), text(r.getLabel(), SpyglassFonts.regular(), SpyglassTheme.TEXT_PRIMARY, WIDTH - (q ? 45 : 5) - (items == null ? 0 : 40)),
				q ? text(r.getQuantity(), SpyglassFonts.regular(), SpyglassTheme.TEXT_PRIMARY, 0) : null));
			tile.add(text(r.getStatus(), SpyglassFonts.small(), tone(r.getTone())));
			if (r.getSupport() != null)
			{
				tile.add(text(r.getSupport(), SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
			}
			page.add(tile);
		}
	}

	private JComponent siteCard(FarmingPreparationView.SiteRow s, int i, int n)
	{
		JPanel tile = tile(null);
		JPanel arrows = new JPanel(new GridLayout(1, 2, 2, 0));
		arrows.setOpaque(false);
		Button up = button("▲", Style.PLAIN, () -> act(() -> host.moveStop(i, -1)));
		up.name("Move " + s.getSite() + " earlier");
		up.setEnabled(i > 0);
		Button down = button("▼", Style.PLAIN, () -> act(() -> host.moveStop(i, 1)));
		down.name("Move " + s.getSite() + " later");
		down.setEnabled(i < n - 1);
		arrows.add(up);
		arrows.add(down);
		tile.add(row(null, text(s.getOrder() + " · " + s.getSite(), SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY, WIDTH - 60), arrows));
		if (s.getTransport() != null)
		{
			tile.add(row(icon(s.getItemId()), text(s.getTransport(), SpyglassFonts.regular(), SpyglassTheme.TEXT_SECONDARY, WIDTH - 50), null));
		}
		if (s.getStatus() != null)
		{
			tile.add(text(s.getStatus(), SpyglassFonts.small(), tone(s.getTone())));
		}
		tile.setToolTipText(s.getDetails().isEmpty() ? null : "<html>" + esc(String.join("\n", s.getDetails())).replace("\n", "<br>") + "</html>");
		return tile;
	}

	// -- the run ------------------------------------------------------------------------------------------------------

	private void run(List<JComponent> page, FarmingRunView run)
	{
		page.add(text(run.getTitle(), SpyglassFonts.bold(), SpyglassTheme.ACCENT_GOLD));
		if (run.getPhase() == FarmingRunView.Phase.COMPLETE)
		{
			boolean ended = run.getIntent().getEndReason() != null;
			JPanel tile = tile(ended ? SpyglassTheme.ACCENT_GOLD : SpyglassTheme.ACCENT_SAGE);
			tile.add(text("✓ " + run.getCountText() + " · " + run.getElapsedText(), SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
			for (String l : run.getSummary())
			{
				tile.add(text(l, SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
			}
			page.add(tile);
			page.add(button("Done", Style.PRIMARY, () -> act(() -> controls().acknowledgeRun())));
			return;
		}
		page.add(text(run.getCountText() + " · " + run.getElapsedText(), SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		if (run.getPhase() == FarmingRunView.Phase.PAUSED)
		{
			JPanel tile = tile(SpyglassTheme.ACCENT_GOLD);
			tile.add(text("Run paused", SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
			tile.add(text("Your progress is kept until you finish or end the run.", SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
			tile.add(button("Resume", Style.PRIMARY, () -> act(() -> controls().resumeRun())));
			page.add(tile);
		}
		else
		{
			GuidanceModel g = host.guidance();
			boolean guided = g != null && g.isActive() && g.getTitle() != null;
			String title = guided ? g.getTitle() : run.getNextTitle();
			List<String> all = guided ? g.getLines() : run.getNextLines();
			String step = guided && g.getGuide() != null ? g.getGuide() : firstOpen(all);
			JPanel tile = tile(SpyglassTheme.ACCENT_GOLD);
			tile.add(text("NEXT", SpyglassFonts.smallBold(), SpyglassTheme.ACCENT_GOLD));
			tile.add(text(title == null ? "Everything here is done" : title, SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
			if (step != null)
			{
				tile.add(text(step, SpyglassFonts.regular(), SpyglassTheme.TEXT_SECONDARY));
			}
			List<String> tip = new ArrayList<>();
			if (guided && g.getTooltip() != null)
			{
				tip.add(g.getTooltip());
			}
			tip.addAll(all);
			tile.setToolTipText("<html>" + esc(String.join("\n", tip)).replace("\n", "<br>") + "</html>");
			page.add(tile);
		}
		for (String group : new String[]{"TO DO", "COMPLETED", "SKIPPED"})
		{
			List<FarmingRunView.PatchLine> lines = new ArrayList<>();
			for (FarmingRunView.PatchLine l : run.getLines())
			{
				if (group.equals(groupOf(l)))
				{
					lines.add(l);
				}
			}
			if (!lines.isEmpty())
			{
				page.add(row(header(group), null, text(String.valueOf(lines.size()), SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED, 0)));
				for (FarmingRunView.PatchLine l : lines)
				{
					page.add(runCard(l));
				}
			}
		}
		List<RunExtras.BinWork> work = run.getBinWork();
		if (!work.isEmpty())
		{
			page.add(header("COMPOST BINS"));
			for (RunExtras.BinWork b : work)
			{
				JPanel tile = tile(b.isDue() ? SpyglassTheme.ACCENT_GOLD : null);
				tile.add(text(b.def.getPlace() + " · Compost bin", SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
				tile.add(text(b.skipped ? "Skipped" : b.task == null ? "Nothing to do now" : b.task.label, SpyglassFonts.small(),
					b.isDue() ? SpyglassTheme.ACCENT_GOLD : SpyglassTheme.TEXT_SECONDARY));
				if (b.task != null || b.skipped)
				{
					tile.add(button(b.skipped ? "Undo skip" : "Skip", Style.LINK, () -> act(() -> controls().setBinSkipped(b.def.getBinId(), !b.skipped))));
				}
				page.add(tile);
			}
		}
		endLink(page, run);
	}

	static String groupOf(FarmingRunView.PatchLine line)
	{
		FarmingRunEvaluator.Outcome o = line.getProgress().getOutcome();
		if (o == FarmingRunEvaluator.Outcome.SKIPPED)
		{
			return "SKIPPED";
		}
		// A payment or compost still due is still to do (§28.16, §28.17).
		return o.isSettled() && !line.isProtectionDue() && !line.isCompostDue() ? "COMPLETED" : "TO DO";
	}

	private JComponent runCard(FarmingRunView.PatchLine line)
	{
		FarmingModel.Row row = line.getRow();
		String name = row == null ? line.getPatchId() : row.getPatch().getAreaName() + " · " + row.getPatch().getType().getDisplayName()
			+ (row.getLocation().endsWith(")") ? " " + row.getLocation().substring(row.getLocation().lastIndexOf('(')) : "");
		FarmingRunEvaluator.Outcome o = line.getProgress().getOutcome();
		JPanel tile = tile(o == FarmingRunEvaluator.Outcome.DONE ? SpyglassTheme.ACCENT_SAGE
			: (line.isCurrent() || line.isHere()) && !o.isSettled() ? SpyglassTheme.ACCENT_GOLD : null);
		boolean skipped = line.isSkipped();
		boolean compostSkipped = line.isCompostSkipped() && o.isSettled();
		boolean compost = !line.isSkippable() && !skipped && (line.isCompostDue() || compostSkipped && !line.isProtectionDue());
		boolean payment = !compost && !line.isSkippable() && !skipped && (line.isProtectionDue() || line.isProtectionSkipped());
		Button skip = null;
		if (line.isSkippable() || skipped || compost || payment)
		{
			boolean undo = compost ? compostSkipped : payment ? line.isProtectionSkipped() : skipped;
			skip = button(undo ? "Undo skip" : compost ? "Skip compost" : payment ? "Skip payment" : "Skip", Style.LINK, () -> act(() ->
			{
				if (compost)
				{
					controls().setCompostSkipped(line.getPatchId(), !compostSkipped);
				}
				else if (payment)
				{
					controls().setProtectionSkipped(line.getPatchId(), !line.isProtectionSkipped());
				}
				else
				{
					controls().setSkipped(line.getPatchId(), !skipped);
				}
			}));
			skip.name((undo ? "Undo skip for " : "Skip ") + name);
		}
		tile.add(row(null, text(name, SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY, WIDTH - 70), skip));
		tile.add(text(line.getRunText(), SpyglassFonts.regular(), color(line.getRunTone())));
		List<String> steps = new ArrayList<>();
		for (FarmingRunView.Step s : line.getSteps())
		{
			steps.add(s.getText());
		}
		if (!steps.isEmpty())
		{
			tile.add(text(String.join("  ", steps), SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		}
		String now = line.getAccessNote() != null ? line.getAccessNote() : line.getCondition();
		if (row != null && (o.isSettled() || line.getAccessNote() != null))
		{
			tile.add(text(now == null || now.isEmpty() ? row.getChip() : now, SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
		}
		return tile;
	}

	private void endLink(List<JComponent> page, FarmingRunView run)
	{
		if (!endConfirm)
		{
			page.add(row(null, null, button("End run", Style.LINK, () ->
			{
				endConfirm = true;
				redraw();
			})));
			return;
		}
		JPanel tile = tile(SpyglassTheme.FARM_EMBER);
		tile.add(text("End this run?", SpyglassFonts.bold(), SpyglassTheme.TEXT_PRIMARY));
		tile.add(text(run.getProgress().getDone() > 0 ? "What you've done so far is kept in a short summary."
			: "Nothing has been done yet, so there's nothing to summarise.", SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		JPanel b = new JPanel(new GridLayout(1, 2, 4, 0));
		b.setOpaque(false);
		b.add(button("End run", Style.PLAIN, () ->
		{
			endConfirm = false;
			act(() -> controls().endRun());
		}));
		b.add(button("Keep going", Style.PRIMARY, () ->
		{
			endConfirm = false;
			redraw();
		}));
		tile.add(b);
		page.add(tile);
	}

	static String firstOpen(List<String> lines)
	{
		for (String l : lines)
		{
			if (l != null && !l.isEmpty() && !l.startsWith("✓"))
			{
				return l.startsWith("○ ") ? l.substring(2) : l;
			}
		}
		return null;
	}

	// -- settings -----------------------------------------------------------------------------------------------------

	private void settings(List<JComponent> page)
	{
		FarmingPreferences p = prefs();
		page.add(header("TRAVEL"));
		page.add(text("How runs choose the way to each stop.", SpyglassFonts.small(), SpyglassTheme.TEXT_SECONDARY));
		for (FarmingPreferences.Travel t : FarmingPreferences.Travel.values())
		{
			page.add(choice(p.getTravel() == t, t.getLabel(), "Travel: ", () -> host.savePreferences(prefs().withTravel(t))));
		}
		page.add(header("TREE CLEARING"));
		for (FarmingPreferences.TreeClearing c : FarmingPreferences.TreeClearing.values())
		{
			page.add(choice(p.getTreeClearing() == c, c == FarmingPreferences.TreeClearing.PAY ? "Pay the gardener · " + FarmingPreferences.REMOVAL_COINS
				+ " coins" : "Chop down, dig up the stump", "Clear grown trees: ", () -> host.savePreferences(prefs().withTreeClearing(c))));
		}
		page.add(text("Redwood: pay Alexandra 2,000 coins. Calquat, spirit, celastrus and crystal trees are cleared by hand.", SpyglassFonts.small(),
			SpyglassTheme.TEXT_MUTED));
		page.add(header("FRUIT"));
		page.add(button((p.isPickFruitFirst() ? "☑ " : "☐ ") + "Pick fruit before clearing", Style.LINK,
			() -> act(() -> host.savePreferences(prefs().withPickFruitFirst(!prefs().isPickFruitFirst())))));
		page.add(header("CROP PROTECTION"));
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 0));
		grid.setOpaque(false);
		for (FarmingRunType t : FarmingRunType.values())
		{
			if (t.offersProtection())
			{
				boolean on = p.isProtect(t);
				grid.add(button((on ? "☑ " : "☐ ") + (t == FarmingRunType.ALLOTMENT ? "Allotments" : t.getTitle()), Style.LINK,
					() -> act(() -> host.savePreferences(prefs().withProtect(t, !on)))));
			}
		}
		page.add(grid);
		page.add(text("Payments follow the planting materials you carry.", SpyglassFonts.small(), SpyglassTheme.TEXT_MUTED));
	}

	private Button choice(boolean on, String label, String name, Runnable save)
	{
		Button b = button((on ? "◉ " : "○ ") + label, Style.LINK, () -> act(save));
		b.name(name + label + (on ? ", selected" : ""));
		return b;
	}

	// -- small parts --------------------------------------------------------------------------------------------------

	enum Style
	{
		PRIMARY, PLAIN, TILE, LINK, ROW
	}

	/** Every control: a flat button pressed by click, keyboard or {@link #press()} (tests). */
	static final class Button extends JButton
	{
		private final Style style;

		Button(String text, Style style, Runnable action)
		{
			super(style == Style.ROW || style == Style.LINK && text.length() > 22 ? "<html><div style='width:" + (style == Style.ROW ? FarmingView.WIDTH - 12
				: FarmingView.WIDTH - 4) + "px'>" + esc(text) + "</div></html>" : text);
			this.style = style;
			addActionListener(e -> action.run());
			setFocusPainted(false);
			setContentAreaFilled(style != Style.LINK);
			setOpaque(style != Style.LINK);
			setHorizontalAlignment(style == Style.LINK || style == Style.ROW ? LEFT : CENTER);
			setFont(style == Style.PRIMARY ? SpyglassFonts.bold() : style == Style.LINK ? SpyglassFonts.small() : SpyglassFonts.regular());
			setForeground(style == Style.PRIMARY ? SpyglassTheme.ACCENT_GOLD : SpyglassTheme.TEXT_PRIMARY);
			setBackground(SpyglassTheme.TILE_FACE);
			setBorder(style == Style.LINK ? BorderFactory.createEmptyBorder(1, 0, 1, 0)
				: BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(SpyglassTheme.BORDER_SOFT), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			name(text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
		}

		void selected(boolean on)
		{
			setBackground(on ? SpyglassTheme.CARD_SELECTED : SpyglassTheme.TILE_FACE);
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(on ? SpyglassTheme.ACCENT_GOLD : SpyglassTheme.BORDER_SOFT),
				BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		}

		void name(String n)
		{
			getAccessibleContext().setAccessibleName(n);
		}

		void press()
		{
			if (isEnabled())
			{
				doClick(0);
			}
		}

		@Override
		public void setEnabled(boolean on)
		{
			super.setEnabled(on);
			setForeground(!on ? SpyglassTheme.TEXT_MUTED : style == Style.PRIMARY ? SpyglassTheme.ACCENT_GOLD
				: style == Style.LINK && getText().contains("☐") ? SpyglassTheme.TEXT_SECONDARY : SpyglassTheme.TEXT_PRIMARY);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	private Button button(String text, Style style, Runnable action)
	{
		sig.append('[').append(text).append(']');
		Button b = new Button(text, style, action);
		b.setEnabled(true);
		return b;
	}

	private JLabel text(String s, Font f, Color c)
	{
		return text(s, f, c, WIDTH);
	}

	/** A wrapping label of at most {@code width} px. */
	private JLabel text(String s, Font f, Color c, int width)
	{
		sig.append('|').append(s);
		JLabel l = new JLabel(width <= 0 ? s : "<html><div style='width:" + width + "px'>" + esc(s == null ? "" : s) + "</div></html>");
		l.setFont(f);
		l.setForeground(c);
		return l;
	}

	private JComponent header(String s)
	{
		return text(s.toUpperCase(Locale.ROOT), SpyglassFonts.smallBold(), SpyglassTheme.TILE_TEXT_QUIET);
	}

	private JLabel icon(int itemId)
	{
		if (items == null || itemId <= 0)
		{
			return null;
		}
		JLabel l = new JLabel();
		l.setPreferredSize(new Dimension(36, 32));
		items.getImage(itemId).addTo(l);
		return l;
	}

	private static JPanel row(JComponent west, JComponent center, JComponent east)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		p.setOpaque(false);
		for (Object[] e : new Object[][]{{west, BorderLayout.WEST}, {center, BorderLayout.CENTER}, {east, BorderLayout.EAST}})
		{
			if (e[0] != null)
			{
				p.add((Component) e[0], e[1]);
			}
		}
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	/** A raised card: a column on the tile face with an optional coloured rail on its left. */
	private static JPanel tile(Color rail)
	{
		JPanel p = new JPanel()
		{
			@Override
			public Component add(Component c)
			{
				((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
				return super.add(c);
			}

			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(SpyglassTheme.TILE_FACE);
		p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 3, 1, 1, rail == null ? SpyglassTheme.TILE_RIM : rail),
			BorderFactory.createEmptyBorder(3, 5, 3, 4)));
		return p;
	}

	static Color rail(FarmingModel.Tone tone)
	{
		return tone == FarmingModel.Tone.GOLD ? SpyglassTheme.ACCENT_GOLD : tone == FarmingModel.Tone.EMBER ? SpyglassTheme.FARM_EMBER : null;
	}

	static Color color(FarmingModel.Tone tone)
	{
		switch (tone)
		{
			case GOLD:
				return SpyglassTheme.ACCENT_GOLD;
			case EMBER:
				return SpyglassTheme.FARM_EMBER;
			case PRIMARY:
				return SpyglassTheme.TEXT_PRIMARY;
			case SAGE:
				return SpyglassTheme.ACCENT_SAGE;
			case GREEN:
				return SpyglassTheme.STATUS_SUCCESS;
			case RED:
				return SpyglassTheme.STATUS_DANGER;
			case MUTED:
				return SpyglassTheme.TEXT_MUTED;
			default:
				return SpyglassTheme.TEXT_SECONDARY;
		}
	}

	static Color tone(FarmingPreparationView.Tone tone)
	{
		switch (tone)
		{
			case DONE:
				return SpyglassTheme.ACCENT_SAGE;
			case ACTION:
				return SpyglassTheme.ACCENT_GOLD;
			case UNVERIFIED:
				return SpyglassTheme.ACCENT_BRONZE;
			case WARN:
				return SpyglassTheme.FARM_EMBER;
			default:
				return SpyglassTheme.TEXT_SECONDARY;
		}
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
