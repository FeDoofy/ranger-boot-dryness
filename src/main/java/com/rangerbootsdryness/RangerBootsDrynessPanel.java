package com.rangerbootsdryness;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class RangerBootsDrynessPanel extends PluginPanel
{
	private final RangerBootsDrynessPlugin plugin;
	private final RangerBootsDrynessConfig config;

	private final JLabel cluesCompletedLabel = new JLabel();
	private final JLabel bootsObtainedLabel = new JLabel();
	private final JLabel stillDryChanceLabel = new JLabel();
	private final JLabel faladorKillsLabel = new JLabel();
	private final JLabel faladorCluesLabel = new JLabel();
	private final JLabel guardDrynessLabel = new JLabel();
	private final JLabel diaryStatusLabel = new JLabel();

	public RangerBootsDrynessPanel(RangerBootsDrynessPlugin plugin, RangerBootsDrynessConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel statsPanel = new JPanel(new GridLayout(0, 1, 0, 6));
		statsPanel.setBorder(BorderFactory.createTitledBorder("Dryness stats"));

		for (JLabel label : new JLabel[]{
			cluesCompletedLabel, bootsObtainedLabel, stillDryChanceLabel,
			faladorKillsLabel, faladorCluesLabel, guardDrynessLabel, diaryStatusLabel
		})
		{
			label.setHorizontalAlignment(SwingConstants.LEFT);
			statsPanel.add(label);
		}

		JPanel calibratePanel = buildCalibrationPanel();

		JButton resetFaladorButton = new JButton("Reset Falador kill counter");
		resetFaladorButton.addActionListener(e -> {
			plugin.resetFaladorGuardStats();
			refresh();
		});

		JPanel bottomPanel = new JPanel(new GridLayout(0, 1, 0, 8));
		bottomPanel.add(calibratePanel);
		bottomPanel.add(resetFaladorButton);

		add(statsPanel, BorderLayout.NORTH);
		add(bottomPanel, BorderLayout.SOUTH);

		refresh();
	}

	private JPanel buildCalibrationPanel()
	{
		JPanel calibratePanel = new JPanel();
		calibratePanel.setLayout(new GridLayout(0, 1, 0, 4));
		calibratePanel.setBorder(BorderFactory.createTitledBorder("Calibrate from collection log"));

		JLabel instructions = new JLabel("<html>Open your collection log &rarr; Clue Scrolls &rarr; "
			+ "Medium, then enter your real lifetime totals below once. "
			+ "The plugin will track everything from here automatically.</html>");
		instructions.setForeground(Color.LIGHT_GRAY);

		JSpinner clueSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
		JSpinner bootsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));

		JPanel clueRow = new JPanel(new BorderLayout());
		clueRow.add(new JLabel("Medium clues completed: "), BorderLayout.WEST);
		clueRow.add(clueSpinner, BorderLayout.CENTER);

		JPanel bootsRow = new JPanel(new BorderLayout());
		bootsRow.add(new JLabel("Ranger boots obtained: "), BorderLayout.WEST);
		bootsRow.add(bootsSpinner, BorderLayout.CENTER);

		JButton applyButton = new JButton("Set as my starting totals");
		applyButton.addActionListener(e -> {
			// Commit any value typed directly into the spinner text fields;
			// without this, getValue() returns the stale (pre-typing) value.
			try
			{
				clueSpinner.commitEdit();
				bootsSpinner.commitEdit();
			}
			catch (java.text.ParseException ignored)
			{
				// Invalid text in a spinner - fall back to last valid values
			}
			int clues = (Integer) clueSpinner.getValue();
			int boots = (Integer) bootsSpinner.getValue();
			plugin.setCalibratedTotals(clues, boots);
			refresh();
		});

		calibratePanel.add(instructions);
		calibratePanel.add(clueRow);
		calibratePanel.add(bootsRow);
		calibratePanel.add(applyButton);

		return calibratePanel;
	}

	public void refresh()
	{
		int cluesSinceBoots = plugin.getCluesSinceLastBoots();
		double stillDryChance = plugin.getProbabilityStillDry(cluesSinceBoots) * 100;
		double guardDryChance = plugin.getProbabilityThisDryOnGuardClues(config.faladorDiaryCompleted()) * 100;

		cluesCompletedLabel.setText("Medium clues completed: " + plugin.getMediumCluesCompleted());
		bootsObtainedLabel.setText("Ranger boots obtained: " + plugin.getRangerBootsObtained());

		stillDryChanceLabel.setText(String.format("Chance you'd still be dry: %.1f%%", stillDryChance));
		stillDryChanceLabel.setForeground(
			stillDryChance < 25 ? Color.RED : (stillDryChance < 60 ? Color.YELLOW.darker() : ColorScheme.PROGRESS_COMPLETE_COLOR));

		faladorKillsLabel.setText("Falador guard kills: " + plugin.getFaladorGuardKills());
		faladorKillsLabel.setToolTipText("Kills detected in the Falador city area");

		double expected = plugin.getExpectedCluesFromFaladorKills(config.faladorDiaryCompleted());
		faladorCluesLabel.setText(String.format("Clues from those kills: %d (exp. %.1f)",
			plugin.getCluesFromFaladorGuards(), expected));

		guardDrynessLabel.setText(String.format("Guard clue dryness: %.1f%%", guardDryChance));
		guardDrynessLabel.setToolTipText("Chance of having this many clues or fewer from your kills - lower means drier");
		guardDrynessLabel.setForeground(
			guardDryChance < 25 ? Color.RED : (guardDryChance < 60 ? Color.YELLOW.darker() : ColorScheme.PROGRESS_COMPLETE_COLOR));

		diaryStatusLabel.setText("Falador medium diary: " + (config.faladorDiaryCompleted() ? "Done (1/106)" : "Not done (1/128)"));
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, 500);
	}
}
