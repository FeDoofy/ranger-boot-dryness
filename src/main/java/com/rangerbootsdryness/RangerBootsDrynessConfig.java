package com.rangerbootsdryness;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rangerbootsdryness")
public interface RangerBootsDrynessConfig extends Config
{
	@ConfigItem(
		keyName = "faladorDiaryCompleted",
		name = "Falador medium diary completed",
		description = "Falador guards drop medium clues at 1/106 instead of 1/128 once this is done.",
		position = 1
	)
	default boolean faladorDiaryCompleted()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show on-screen overlay",
		description = "Toggles the always-on overlay. The side panel always works regardless of this setting.",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}
}
