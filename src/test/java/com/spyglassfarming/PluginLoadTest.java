package com.spyglassfarming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.farming.RunHarness;
import com.spyglassfarming.storage.TestFilepaths;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import org.junit.Test;

/**
 * The plugin the way RuneLite builds it: Guice injects it with the client's own services, it starts, watches patches through
 * the event bus, and stops cleanly. Nothing from Spyglass is on this classpath, so a pass is a pass with Spyglass absent.
 *
 * Its data folder comes from RuneLite's real {@code Plugin.getPluginDirectory()}: the Gradle test task points RuneLite's home
 * at a throw-away folder in the build directory, so the lookup the client makes runs here as it does on Windows.
 */
public class PluginLoadTest
{
	/** The Plugin Hub's rule for a plugin's internal name (its Hub file name), from the Hub packager. */
	private static final String HUB_INTERNAL_NAME = "^[a-z0-9-]+$";

	/**
	 * The plugin with its sidebar button and overlay recorded instead of drawn. RuneLite's descriptor is not inherited, so this
	 * subclass repeats the real one; {@link #descriptor_isTheHubIdentity} checks they agree.
	 */
	@PluginDescriptor(name = "Farmbound", internalName = SpyglassFarmingPlugin.INTERNAL_NAME)
	public static class TestPlugin extends SpyglassFarmingPlugin
	{
		final List<NavigationButton> buttons = new ArrayList<>();
		final List<NavigationButton> removed = new ArrayList<>();
		int overlays;
		/** Makes the next start-up fail after its button is added (a late start-up failure). */
		boolean failAfterButton;

		@Override
		void addUi(NavigationButton button)
		{
			buttons.add(button);
			if (failAfterButton)
			{
				failAfterButton = false;
				throw new IllegalStateException("overlay could not be added (test)");
			}
			overlays++;
		}

		@Override
		void removeUi(NavigationButton button)
		{
			assertNotNull("never asked to remove a button that was not created", button);
			removed.add(button);
			buttons.remove(button);
			if (overlays > 0)
			{
				overlays--;
			}
		}
	}

	/** The plugin without an internal name, as it was when the Windows launch failed. */
	@PluginDescriptor(name = "Farmbound (no internal name)")
	public static class NoInternalName extends TestPlugin
	{
	}

	/** The throw-away home the Gradle test task gives RuneLite; refuses to run against a real ~/.runelite. */
	private static Path testHome()
	{
		String home = System.getProperty("spyglassfarming.testHome");
		assertNotNull("run through Gradle: its test task points RuneLite at a throw-away home", home);
		assertEquals("RuneLite's folder is the throw-away one", new File(home, ".runelite").getAbsoluteFile(), RuneLite.RUNELITE_DIR.getAbsoluteFile());
		return Path.of(home);
	}

	private static Injector injector(RunHarness.Fake fake, EventBus bus)
	{
		return Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(fake.client);
				bind(ClientToolbar.class).toInstance(allocate(ClientToolbar.class)); // injected, never called: the plugin's UI calls are the test's seam
				bind(OverlayManager.class).toInstance(allocate(OverlayManager.class));
				bind(EventBus.class).toInstance(bus);
				bind(Gson.class).toInstance(new Gson());
				bind(net.runelite.client.game.ItemManager.class).toInstance(allocate(net.runelite.client.game.ItemManager.class));
				bind(net.runelite.client.callback.ClientThread.class).toInstance(allocate(net.runelite.client.callback.ClientThread.class));
				bind(net.runelite.client.config.ConfigManager.class).toInstance(allocate(net.runelite.client.config.ConfigManager.class));
				bind(SpyglassFarmingConfig.class).toInstance(new SpyglassFarmingConfig()
				{
				});
				bindConstant().annotatedWith(Names.named("developerMode")).to(false);
			}
		});
	}

	/** Every regular file under {@code dir} with its bytes, to show a folder was left exactly as it was. */
	private static Map<String, String> contents(Path dir) throws IOException
	{
		Map<String, String> out = new TreeMap<>();
		if (Files.exists(dir))
		{
			try (Stream<Path> s = Files.walk(dir))
			{
				for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator)
				{
					out.put(dir.relativize(p).toString(), new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8));
				}
			}
		}
		return out;
	}

	private static void write(Path file, String text) throws IOException
	{
		Files.createDirectories(file.getParent());
		Files.write(file, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	@Test
	public void descriptor_isTheHubIdentity()
	{
		PluginDescriptor real = SpyglassFarmingPlugin.class.getAnnotation(PluginDescriptor.class);
		assertEquals("spyglass-farming", real.internalName());
		assertEquals(SpyglassFarmingPlugin.INTERNAL_NAME, real.internalName());
		assertTrue("a valid Hub file name (lowercase letters, digits, hyphens)", real.internalName().matches(HUB_INTERNAL_NAME));
		assertFalse("never Spyglass's own identity", real.internalName().equals("spyglass"));
		assertEquals("no legacy folder: RuneLite would move it into this plugin's", "", real.legacyDataDirectory());
		PluginDescriptor test = TestPlugin.class.getAnnotation(PluginDescriptor.class);
		assertEquals("the test plugin repeats the real identity", real.internalName(), test.internalName());
		assertEquals(real.legacyDataDirectory(), test.legacyDataDirectory());
	}

	@Test
	public void realPluginClass_getsItsOwnDataFolder_fromRuneLite() throws Exception
	{
		Path home = testHome();
		Method lookup = net.runelite.client.plugins.Plugin.class.getDeclaredMethod("getPluginDirectory");
		lookup.setAccessible(true);
		Filepath dir = (Filepath) lookup.invoke(new SpyglassFarmingPlugin());
		assertEquals(home.resolve(".runelite").resolve("plugin-data").resolve("spyglass-farming").toAbsolutePath(),
			TestFilepaths.path(dir).toAbsolutePath());
		assertEquals(RuneLite.PLUGIN_DATA.resolve("spyglass-farming").toAbsolutePath(), TestFilepaths.path(dir).toAbsolutePath());
	}

	@SuppressWarnings("unchecked")
	private static <T> T allocate(Class<T> type)
	{
		try
		{
			java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (T) ((sun.misc.Unsafe) f.get(null)).allocateInstance(type);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void loads_watchesPatches_andStopsCleanly() throws Exception
	{
		assertNotNull("the plugin is declared for the Hub", SpyglassFarmingPlugin.class.getAnnotation(PluginDescriptor.class));
		assertEquals("Farmbound", SpyglassFarmingPlugin.class.getAnnotation(PluginDescriptor.class).name());
		Path pluginData = testHome().resolve(".runelite").resolve("plugin-data");
		// Spyglass's records (and its pre-Filepath folder) sit beside this plugin's; they must come through untouched.
		Path spyglass = pluginData.resolve("spyglass");
		Path spyglassLegacy = testHome().resolve(".runelite").resolve("osrs-telemetry");
		write(spyglass.resolve(RunHarness.ACCOUNT + "/profiles/standard/farming/farming-state.json"), "{\"spyglass\":true}");
		write(spyglassLegacy.resolve(RunHarness.ACCOUNT + "/state.json"), "{\"legacy\":true}");
		Map<String, String> spyglassBefore = contents(spyglass);
		Map<String, String> legacyBefore = contents(spyglassLegacy);

		RunHarness.Fake fake = new RunHarness.Fake();
		EventBus bus = new EventBus();
		Injector injector = injector(fake, bus);
		TestPlugin plugin = injector.getInstance(TestPlugin.class);
		try
		{
			plugin.startUp();
			bus.register(plugin);
			assertEquals("one sidebar button", 1, plugin.buttons.size());
			assertEquals("Farmbound", plugin.buttons.get(0).getTooltip());
			assertNotNull(plugin.buttons.get(0).getPanel());
			assertEquals("the highlight overlay", 1, plugin.overlays);

			// The plugin reads patches through the event bus on its own: a ripe herb patch becomes known.
			fake.varbits.putAll(Map.of(RunHarness.A, 3, RunHarness.B, 3, RunHarness.C, 3, RunHarness.D, 36));
			for (int i = 0; i < 300; i++)
			{
				fake.tick++;
				bus.post(new GameTick());
				if (i > 8)
				{
					Thread.sleep(5);
				}
			}
			com.spyglassfarming.farming.FarmingCaptureAdapter adapter = injector.getInstance(com.spyglassfarming.farming.FarmingCaptureAdapter.class);
			FarmingSnapshot snap = adapter.uiSnapshot();
			assertEquals(FarmingSnapshot.State.AVAILABLE, snap.getState());
			assertTrue("herb patch seen: " + snap.getKnowledge().keySet(), snap.getKnowledge().containsKey("herb.falador"));

		}
		finally
		{
			plugin.shutDown();
		}
		assertTrue("sidebar button removed", plugin.buttons.isEmpty());
		assertTrue("overlay removed", plugin.overlays == 0);

		// What it saved is in its own RuneLite folder; Spyglass's records and folder are exactly as they were.
		Map<String, String> own = contents(pluginData.resolve("spyglass-farming"));
		assertTrue("its records are saved under plugin-data/spyglass-farming: " + own.keySet(),
			own.keySet().stream().anyMatch(k -> k.startsWith(RunHarness.ACCOUNT + java.io.File.separator)));
		assertEquals(spyglassBefore, contents(spyglass));
		assertEquals(legacyBefore, contents(spyglassLegacy));
		try (Stream<Path> s = Files.list(pluginData))
		{
			assertEquals("no other plugin folder is created", Set.of("spyglass", "spyglass-farming"),
				s.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
		}

		// Enabling it again works (a re-enable after a disable).
		plugin.startUp();
		assertEquals(1, plugin.buttons.size());
		plugin.shutDown();
		assertTrue(plugin.buttons.isEmpty());
	}

	@Test
	public void withoutAnInternalName_startUpFails_andShutDownIsSafe() throws Exception
	{
		testHome();
		NoInternalName plugin = injector(new RunHarness.Fake(), new EventBus()).getInstance(NoInternalName.class);
		try
		{
			plugin.startUp();
			fail("RuneLite gives no data folder without an internal name");
		}
		catch (IllegalArgumentException e)
		{
			assertEquals("internalName must be set in @PluginDescriptor.", e.getMessage());
		}
		// RuneLite stops a plugin whose start-up threw: nothing was put on screen, so nothing is removed.
		plugin.shutDown();
		assertTrue(plugin.buttons.isEmpty());
		assertTrue("no removal of a button that was never created", plugin.removed.isEmpty());
		plugin.shutDown();
		assertTrue(plugin.removed.isEmpty());
	}

	@Test
	public void lateStartUpFailure_removesOnlyTheButtonItCreated_andCanStartAgain() throws Exception
	{
		testHome();
		TestPlugin plugin = injector(new RunHarness.Fake(), new EventBus()).getInstance(TestPlugin.class);
		plugin.failAfterButton = true;
		try
		{
			plugin.startUp();
			fail("start-up should fail after the button was added");
		}
		catch (IllegalStateException expected)
		{
			// the overlay step failed
		}
		assertEquals(1, plugin.buttons.size());
		NavigationButton created = plugin.buttons.get(0);
		plugin.shutDown();
		assertEquals("the button that was created is removed, once", List.of(created), plugin.removed);
		assertTrue(plugin.buttons.isEmpty());
		plugin.shutDown();
		assertEquals("a second stop removes nothing", 1, plugin.removed.size());

		plugin.startUp();
		assertEquals(1, plugin.buttons.size());
		assertEquals(1, plugin.overlays);
		plugin.shutDown();
		assertTrue(plugin.buttons.isEmpty());
		assertEquals(0, plugin.overlays);
	}

	@Test
	public void nothingFromSpyglass_isReferencedOrOnTheClasspath() throws Exception
	{
		try
		{
			Class.forName("com.spyglassfarming.OsrsTelemetryPlugin");
			fail("Spyglass classes must not be reachable");
		}
		catch (ClassNotFoundException expected)
		{
			// the plugin stands alone
		}
		Path classes = Path.of(SpyglassFarmingPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		int scanned = 0;
		try (Stream<Path> s = Files.walk(classes))
		{
			for (Path p : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".class"))::iterator)
			{
				scanned++;
				String bytes = new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.ISO_8859_1);
				assertFalse(p + " references Spyglass", bytes.contains("com/osrstelemetry"));
			}
		}
		assertTrue(scanned > 50);
	}
}
