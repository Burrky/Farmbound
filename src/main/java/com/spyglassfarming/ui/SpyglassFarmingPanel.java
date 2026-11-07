package com.spyglassfarming.ui;

import com.spyglassfarming.farming.FarmingAccountFacts;
import com.spyglassfarming.farming.FarmingCompost;
import com.spyglassfarming.farming.FarmingModel;
import com.spyglassfarming.farming.FarmingPatchCatalog;
import com.spyglassfarming.farming.FarmingPatchType;
import com.spyglassfarming.farming.FarmingPreferences;
import com.spyglassfarming.farming.FarmingPreparationView;
import com.spyglassfarming.farming.FarmingRunControls;
import com.spyglassfarming.farming.FarmingRunRequest;
import com.spyglassfarming.farming.FarmingRunStore;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.guidance.GuidanceModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar: the original Farming screens (overview, combined selection, preparation, the run), with
 * a Settings toggle. Everything is read through {@link Host}; the view re-renders once a second while shown.
 */
public final class SpyglassFarmingPanel extends PluginPanel
{
	/** What the panel reads and where the player's choices go (the plugin; a fake in tests). */
	public interface Host
	{
		FarmingSnapshot snapshot();

		FarmingAccountFacts facts();

		FarmingRunControls controls();

		FarmingPreparationView preparation();

		GuidanceModel guidance();

		FarmingPreferences preferences();

		void savePreferences(FarmingPreferences p);

		String selection();

		void saveSelection(String s);

		/** Move the stop at {@code index} in the run's order by {@code delta} (the preparation page's arrows). */
		void moveStop(int index, int delta);

		/** Preparation's optional house setup: what the house was confirmed to have, and how to confirm it. */
		List<String> houseSetup();

		long now();
	}


	private final ItemManager itemManager;
	private final Host host;
	private final JPanel farmingHolder = new JPanel(new BorderLayout());
	private final Timer timer;
	private FarmingView view;
	private FarmingView.Button farmingTab;
	private FarmingView.Button settingsTab;

	public SpyglassFarmingPanel(ItemManager itemManager, Host host)
	{
		super(false);
		this.itemManager = itemManager;
		this.host = host;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		farmingHolder.setOpaque(false);
		add(scroll(farmingHolder), BorderLayout.CENTER);
		rebuild(1f);
		timer = new Timer(1000, e ->
		{
			if (isShowing())
			{
				refresh();
			}
		});
		timer.start();
	}

	/** Builds the header and the Farming view again (at a new text size: every font is set when a component is built). */
	public void rebuild(float textScale)
	{
		SpyglassFonts.setScale(textScale);
		boolean settings = view != null && view.isSettingsOpen();
		JPanel header = new JPanel(new GridLayout(1, 2, 4, 4));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(4, BORDER_OFFSET, 4, BORDER_OFFSET));
		farmingTab = new FarmingView.Button("Farming", FarmingView.Style.PLAIN, () -> setSettingsOpen(false));
		settingsTab = new FarmingView.Button("Settings", FarmingView.Style.PLAIN, () -> setSettingsOpen(!view.isSettingsOpen()));
		header.add(farmingTab);
		header.add(settingsTab);
		BorderLayout layout = (BorderLayout) getLayout();
		Component old = layout.getLayoutComponent(BorderLayout.NORTH);
		if (old != null)
		{
			remove(old);
		}
		add(header, BorderLayout.NORTH);
		view = new FarmingView(itemManager, host, this::refresh);
		farmingHolder.removeAll();
		farmingHolder.add(view, BorderLayout.NORTH);
		view.setSettingsOpen(settings);
		syncTabs();
		refresh();
		revalidate();
		repaint();
	}

	FarmingView view()
	{
		return view;
	}

	/** Re-renders Farming from fresh state (on the event thread). */
	public void refresh()
	{
		FarmingSnapshot snap;
		FarmingAccountFacts facts;
		try
		{
			snap = host.snapshot();
			facts = host.facts();
		}
		catch (RuntimeException e)
		{
			snap = FarmingSnapshot.LOGGED_OUT;
			facts = FarmingAccountFacts.NONE;
		}
		view.render(FarmingModel.build(FarmingPatchCatalog.standard(), snap == null ? FarmingSnapshot.LOGGED_OUT : snap, host.now(),
			facts == null ? FarmingAccountFacts.ALL_OPEN : facts));
	}

	public void stop()
	{
		timer.stop();
	}

	void setSettingsOpen(boolean open)
	{
		view.setSettingsOpen(open);
		syncTabs();
	}

	private void syncTabs()
	{
		boolean settings = view != null && view.isSettingsOpen();
		farmingTab.selected(!settings);
		settingsTab.selected(settings);
	}

	private static JScrollPane scroll(JComponent c)
	{
		// RuneLite's own wrap: 225 px preferred, 213 px of content inside the gutter, not Scrollable (what the views were built for).
		JPanel top = new JPanel(new BorderLayout())
		{
			@Override
			public java.awt.Dimension getPreferredSize()
			{
				return new java.awt.Dimension(PANEL_WIDTH, super.getPreferredSize().height);
			}
		};
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.setBorder(BorderFactory.createEmptyBorder(0, BORDER_OFFSET, BORDER_OFFSET, BORDER_OFFSET));
		top.add(c, BorderLayout.NORTH);
		JScrollPane pane = new JScrollPane(top);
		pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		pane.setBorder(null);
		pane.getVerticalScrollBar().setUnitIncrement(16);
		return pane;
	}
}
