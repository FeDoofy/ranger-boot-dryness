package com.rangerbootsdryness;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RangerBootsDrynessPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RangerBootsDrynessPlugin.class);
		RuneLite.main(args);
	}
}
