package com.rangerbootsdryness;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class RangerBootsDrynessOverlay extends OverlayPanel
{
	private final RangerBootsDrynessPlugin plugin;
	private final RangerBootsDrynessConfig config;

	@Inject
	private RangerBootsDrynessOverlay(RangerBootsDrynessPlugin plugin, RangerBootsDrynessConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}

		panelComponent.getChildren().clear();

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Ranger Boots Dryness")
			.color(Color.ORANGE)
			.build());

		int cluesSinceBoots = plugin.getCluesSinceLastBoots();
		double stillDryChance = plugin.getProbabilityStillDry(cluesSinceBoots) * 100;
		double guardDryChance = plugin.getProbabilityThisDryOnGuardClues(config.faladorDiaryCompleted()) * 100;

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Medium clues completed:")
			.right(String.valueOf(plugin.getMediumCluesCompleted()))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Ranger boots obtained:")
			.right(String.valueOf(plugin.getRangerBootsObtained()))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Chance you'd still be dry:")
			.right(String.format("%.1f%%", stillDryChance))
			.rightColor(stillDryChance < 25 ? Color.RED : (stillDryChance < 60 ? Color.YELLOW : Color.GREEN))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Falador guard kills:")
			.right(String.valueOf(plugin.getFaladorGuardKills()))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Clues from those kills:")
			.right(String.format("%d (exp. %.1f)",
				plugin.getCluesFromFaladorGuards(),
				plugin.getExpectedCluesFromFaladorKills(config.faladorDiaryCompleted())))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Guard clue dryness:")
			.right(String.format("%.1f%%", guardDryChance))
			.rightColor(guardDryChance < 25 ? Color.RED : (guardDryChance < 60 ? Color.YELLOW : Color.GREEN))
			.build());

		return super.render(graphics);
	}
}
