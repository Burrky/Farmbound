package com.spyglassfarming;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SpyglassFarmingConfig.GROUP)
public interface SpyglassFarmingConfig extends Config
{
	String GROUP = "spyglassfarming";

	@AllArgsConstructor
	enum TextSize
	{
		STANDARD("Standard", 1f),
		LARGE("Large", 1.25f),
		EXTRA_LARGE("Extra large", 1.5f);

		private final String label;
		@Getter
		private final float scale;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(keyName = "textSize", name = "Sidebar text size", description = "The size of the text in the Farmbound sidebar")
	default TextSize textSize()
	{
		return TextSize.STANDARD;
	}
}
