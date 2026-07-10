package com.rangerbootsdryness;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Ranger Boots Dryness Tracker",
	description = "Tracks how dry you are on ranger boots from medium clues, and Falador guard clue drops",
	tags = {"clue", "dryness", "ranger", "boots", "falador"}
)
public class RangerBootsDrynessPlugin extends Plugin
{
	// --- Drop rate constants, sourced from the OSRS Wiki ---
	// Ranger boots: 1/1133 per reward slot, ~3-5 slots per medium casket -> effective ~1/283.6 per casket
	static final double RANGER_BOOTS_RATE_PER_CASKET = 1.0 / 283.6;
	// Falador guards: 1/128 base, 1/106 with the medium Falador Diary completed
	static final double FALADOR_GUARD_CLUE_RATE_BASE = 1.0 / 128.0;
	static final double FALADOR_GUARD_CLUE_RATE_DIARY = 1.0 / 106.0;

	private static final Pattern CLUE_COMPLETE_PATTERN =
		Pattern.compile("You have completed ([0-9,]+) medium Treasure Trails?\\.");
	private static final Pattern COLLECTION_LOG_PATTERN =
		Pattern.compile("New item added to your collection log: (.+)");

	// Rough bounding box for Falador (city walls + immediate surrounds).
	// This is an approximation - if kills near the edge of the map aren't being
	// picked up, widen these values. Plane 0 only (guards don't patrol upstairs).
	private static final int FALADOR_MIN_X = 2925;
	private static final int FALADOR_MAX_X = 3070;
	private static final int FALADOR_MIN_Y = 3300;
	private static final int FALADOR_MAX_Y = 3400;

	@Inject
	private Client client;

	@Inject
	private RangerBootsDrynessConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RangerBootsDrynessOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	private RangerBootsDrynessPanel panel;
	private NavigationButton navButton;

	// --- Persisted stats (survive client restarts) ---
	private int mediumCluesCompleted = 0;   // lifetime total, from chat message
	private int rangerBootsObtained = 0;    // lifetime total, from collection log message
	private int cluesAtLastBoots = 0;       // mediumCluesCompleted value at the last boots pickup
	private int faladorGuardKills = 0;      // session + persisted count of Falador guard kills
	private int cluesFromFaladorGuards = 0; // medium clues attributed to Falador guard kills

	private int previousMediumClueCount = -1;
	private int lastFaladorKillTick = -10;

	@Provides
	RangerBootsDrynessConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RangerBootsDrynessConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadState();

		panel = new RangerBootsDrynessPanel(this, config);
		final BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		navButton = NavigationButton.builder()
			.tooltip("Ranger Boots Dryness")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		if (config.showOverlay())
		{
			overlayManager.add(overlay);
		}
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();

		Matcher clueMatcher = CLUE_COMPLETE_PATTERN.matcher(message);
		if (clueMatcher.find())
		{
			String numberStr = clueMatcher.group(1).replace(",", "");
			mediumCluesCompleted = Integer.parseInt(numberStr);
			saveState();
			if (panel != null)
			{
				panel.refresh();
			}
			return;
		}

		Matcher logMatcher = COLLECTION_LOG_PATTERN.matcher(message);
		if (logMatcher.find())
		{
			String itemName = logMatcher.group(1).trim();
			if (itemName.equalsIgnoreCase("Ranger boots"))
			{
				rangerBootsObtained++;
				cluesAtLastBoots = mediumCluesCompleted;
				saveState();
				if (panel != null)
				{
					panel.refresh();
				}
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();

		if (!npc.isDead())
		{
			return;
		}

		String name = npc.getName();
		if (name == null || !name.equalsIgnoreCase("Guard"))
		{
			return;
		}

		WorldPoint loc = npc.getWorldLocation();
		if (!isInFalador(loc))
		{
			return;
		}

		faladorGuardKills++;
		lastFaladorKillTick = client.getTickCount();
		saveState();
		if (panel != null)
		{
			panel.refresh();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null)
		{
			return;
		}

		int currentCount = inventory.count(ItemID.CLUE_SCROLL_MEDIUM);

		if (previousMediumClueCount != -1 && currentCount > previousMediumClueCount)
		{
			int gained = currentCount - previousMediumClueCount;
			// Only attribute this pickup to a Falador guard if we killed one this tick or last tick
			if (client.getTickCount() - lastFaladorKillTick <= 1)
			{
				cluesFromFaladorGuards += gained;
				saveState();
				if (panel != null)
				{
					panel.refresh();
				}
			}
		}

		previousMediumClueCount = currentCount;
	}

	private boolean isInFalador(WorldPoint point)
	{
		if (point == null || point.getPlane() != 0)
		{
			return false;
		}
		return point.getX() >= FALADOR_MIN_X && point.getX() <= FALADOR_MAX_X
			&& point.getY() >= FALADOR_MIN_Y && point.getY() <= FALADOR_MAX_Y;
	}

	// --- Persistence ---

	private static final String CONFIG_GROUP = "rangerbootsdryness";

	private void loadState()
	{
		mediumCluesCompleted = getInt("mediumCluesCompleted", 0);
		rangerBootsObtained = getInt("rangerBootsObtained", 0);
		cluesAtLastBoots = getInt("cluesAtLastBoots", 0);
		faladorGuardKills = getInt("faladorGuardKills", 0);
		cluesFromFaladorGuards = getInt("cluesFromFaladorGuards", 0);
	}

	private void saveState()
	{
		configManager.setConfiguration(CONFIG_GROUP, "mediumCluesCompleted", mediumCluesCompleted);
		configManager.setConfiguration(CONFIG_GROUP, "rangerBootsObtained", rangerBootsObtained);
		configManager.setConfiguration(CONFIG_GROUP, "cluesAtLastBoots", cluesAtLastBoots);
		configManager.setConfiguration(CONFIG_GROUP, "faladorGuardKills", faladorGuardKills);
		configManager.setConfiguration(CONFIG_GROUP, "cluesFromFaladorGuards", cluesFromFaladorGuards);
	}

	private int getInt(String key, int defaultValue)
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, key);
		if (value == null)
		{
			return defaultValue;
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	// --- Getters used by the overlay and panel ---

	public int getMediumCluesCompleted()
	{
		return mediumCluesCompleted;
	}

	public int getRangerBootsObtained()
	{
		return rangerBootsObtained;
	}

	public int getCluesSinceLastBoots()
	{
		return mediumCluesCompleted - cluesAtLastBoots;
	}

	public int getFaladorGuardKills()
	{
		return faladorGuardKills;
	}

	public int getCluesFromFaladorGuards()
	{
		return cluesFromFaladorGuards;
	}

	public double getExpectedCluesFromFaladorKills(boolean diaryDone)
	{
		double rate = diaryDone ? FALADOR_GUARD_CLUE_RATE_DIARY : FALADOR_GUARD_CLUE_RATE_BASE;
		return faladorGuardKills * rate;
	}

	/**
	 * Probability that a player would still be dry (no ranger boots) after this many clues,
	 * assuming independent rolls at the wiki's average effective rate.
	 */
	public double getProbabilityStillDry(int cluesSinceLastBoots)
	{
		return Math.pow(1.0 - RANGER_BOOTS_RATE_PER_CASKET, cluesSinceLastBoots);
	}

	/**
	 * Probability that a player with this many Falador guard kills would have received
	 * this many medium clues *or fewer* (binomial CDF). A low percentage means you are
	 * unusually dry on clue drops; ~50% means you're right around the expected rate.
	 * Uses an iterative recurrence to stay numerically stable for large kill counts.
	 */
	public double getProbabilityThisDryOnGuardClues(boolean diaryDone)
	{
		int n = faladorGuardKills;
		int observed = cluesFromFaladorGuards;
		double p = diaryDone ? FALADOR_GUARD_CLUE_RATE_DIARY : FALADOR_GUARD_CLUE_RATE_BASE;

		if (n <= 0)
		{
			return 1.0;
		}
		if (observed >= n)
		{
			return 1.0;
		}

		// P(X = 0) = (1-p)^n, then P(X = k+1) = P(X = k) * (n-k)/(k+1) * p/(1-p)
		double pmf = Math.pow(1.0 - p, n);
		double cdf = pmf;
		double ratio = p / (1.0 - p);
		for (int k = 0; k < observed; k++)
		{
			pmf = pmf * ((double) (n - k) / (k + 1)) * ratio;
			cdf += pmf;
		}
		return Math.min(cdf, 1.0);
	}

	/**
	 * Allows the panel to manually set lifetime totals once, calibrated from the
	 * player's actual collection log, since the chat-based tracking above only
	 * counts activity from when the plugin was first enabled onward.
	 */
	public void setCalibratedTotals(int lifetimeMediumClues, int lifetimeRangerBoots)
	{
		this.mediumCluesCompleted = lifetimeMediumClues;
		this.rangerBootsObtained = lifetimeRangerBoots;
		if (lifetimeRangerBoots == 0)
		{
			// Never received boots: the entire lifetime clue count is the dry streak
			this.cluesAtLastBoots = 0;
		}
		else
		{
			// Boots received at some unknown point in the past: we can't know the
			// true streak, so start counting the current dry streak from here
			this.cluesAtLastBoots = lifetimeMediumClues;
		}
		saveState();
	}

	public void resetFaladorGuardStats()
	{
		this.faladorGuardKills = 0;
		this.cluesFromFaladorGuards = 0;
		saveState();
	}
}
