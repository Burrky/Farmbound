package com.spyglassfarming;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Local development launcher only: never packaged or shipped, and not a JUnit test (it has no test methods), so
 * {@code gradlew test} skips it. {@code gradlew run} loads this plugin like a built-in one and starts the RuneLite client,
 * which is how to try it by hand. This is RuneLite's own example-plugin launcher pattern.
 */
public class SpyglassFarmingPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SpyglassFarmingPlugin.class);
		RuneLite.main(args);
	}
}
