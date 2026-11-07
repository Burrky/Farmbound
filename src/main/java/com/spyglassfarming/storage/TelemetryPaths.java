package com.spyglassfarming.storage;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * All telemetry files live under this plugin's own RuneLite-managed
 * plugin-data directory, keyed by accountHash (not display name) so a
 * rename never orphans history:
 *
 *   .runelite/plugin-data/{internalName}/{accountHash}/
 *
 * The root is Plugin.getPluginDirectory() for internalName "spyglass-farming"
 * (see SpyglassFarmingPlugin.INTERNAL_NAME), so this plugin never shares a
 * folder with Spyglass (plugin-data/spyglass). No legacyDataDirectory is
 * declared: RuneLite would move such a folder into this one. Old Spyglass
 * records are only ever copied, by the explicit import.
 *
 * ROOT LIFECYCLE: init() must be called exactly once (repeat calls with
 * the same value are harmless) before any other method on this class is
 * used -- from SpyglassFarmingPlugin.startUp() in production, or from a
 * throwaway Filepath rooted at a JVM temp directory in tests (see
 * TestFilepaths). Every method below throws IllegalStateException if
 * called before init().
 */
@Slf4j
public final class TelemetryPaths
{
	private TelemetryPaths()
	{
	}

	private static volatile Filepath root;

	/**
	 * Called from SpyglassFarmingPlugin.startUp(), with the
	 * Filepath its own getPluginDirectory() returns -- never touched
	 * again for the lifetime of the client process. A plugin
	 * disable/re-enable cycle calls startUp() again, but
	 * getPluginDirectory() resolves to the exact same location every
	 * time (it depends only on RuneLite's own static PLUGIN_DATA
	 * directory and this plugin's fixed internalName), so re-calling
	 * init() on every startUp() is harmless and deliberately not
	 * guarded against.
	 */
	public static void init(Filepath pluginDirectory)
	{
		root = pluginDirectory;
	}

	private static Filepath root()
	{
		Filepath r = root;
		if (r == null)
		{
			throw new IllegalStateException("TelemetryPaths.init() must be called before any storage path is resolved.");
		}
		return r;
	}

	public static Filepath accountDir(long accountHash)
	{
		Filepath dir = root().joinSegment(Long.toString(accountHash));
		createDirectoriesQuietly(dir);
		return dir;
	}

	/**
	 * Mirrors the old File-based "mkdirs() if missing" convention --
	 * Filepath.createDirectories() is idempotent exactly like mkdirs()
	 * (a no-op success if the directory already exists), the one
	 * difference being that it can throw IOException where mkdirs()
	 * returned a boolean nobody checked. Logged and swallowed here
	 * rather than propagated: a directory that fails to create will
	 * surface anyway, immediately and more specifically, the moment a
	 * caller tries to write into it (see LocalStateStore's own warn
	 * logging on write failure) -- this is purely defensive, not a new
	 * failure mode.
	 */
	/**
	 * A RuneScape-profile-scoped folder inside the account folder:
	 * {@code {accountHash}/profiles/{profileKey}/}. New in the Home / Farming
	 * era: only new data lives here (Farming, later POH), and only the
	 * "standard" profile is used. Existing account-level data is untouched
	 * (canonical architecture §22, ARCH-DEBT-PROFILE).
	 */
	public static Filepath profileDir(long accountHash, String profileKey)
	{
		Filepath dir = accountDir(accountHash).joinSegment("profiles").joinSegment(profileKey);
		createDirectoriesQuietly(dir);
		return dir;
	}

	/** {@code {accountHash}/profiles/{profileKey}/farming/}: patches.json, observations.jsonl, calibration.json. */
	public static Filepath farmingDir(long accountHash, String profileKey)
	{
		Filepath dir = profileDir(accountHash, profileKey).joinSegment("farming");
		createDirectoriesQuietly(dir);
		return dir;
	}

	/** {@code {accountHash}/profiles/{profileKey}/account/}: storages.json (Phase F account capability evidence). */
	public static Filepath accountCapabilityDir(long accountHash, String profileKey)
	{
		Filepath dir = profileDir(accountHash, profileKey).joinSegment("account");
		createDirectoriesQuietly(dir);
		return dir;
	}

	private static void createDirectoriesQuietly(Filepath dir)
	{
		try
		{
			dir.createDirectories();
		}
		catch (IOException e)
		{
			log.warn("Failed creating telemetry directory {}", dir, e);
		}
	}
}
