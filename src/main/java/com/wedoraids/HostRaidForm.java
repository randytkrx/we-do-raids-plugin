/*
 * Copyright (c) 2026, s59
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wedoraids;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

final class HostRaidForm extends JPanel
{
	private final HostDependencies dependencies;
	private final JComboBox<String> raidCombo = new JComboBox<>(new String[]{"ToB", "CoX", "ToA"});
	private final RaidTabButton[] raidTabs = new RaidTabButton[3];
	private final HostRaidFormFields fields;
	private final JPanel details = new JPanel();
	private final JButton postButton = new JButton("Post to Discord");
	private final JButton cancelEditButton = new JButton("Cancel edit");
	private final JLabel status = new JLabel(" ");
	private boolean raidChosen;

	HostRaidForm(HostDependencies dependencies, Runnable submit, Runnable cancelEdit)
	{
		this.dependencies = dependencies;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setVisible(false);
		WdrTheme.styleCombo(raidCombo);
		add(buildRaidTabs());
		add(Box.createVerticalStrut(4));
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setOpaque(false);
		details.setVisible(false);
		details.setAlignmentX(Component.LEFT_ALIGNMENT);
		fields = new HostRaidFormFields(dependencies, this::selectedRaid, this::setStatus);
		details.add(fields);
		details.add(Box.createVerticalStrut(2));
		buildActions(submit, cancelEdit);
		add(details);
		raidCombo.addActionListener(e -> fields.refreshForRaid());
	}

	void prepareExpanded()
	{
		fields.prepareExpanded();
	}

	void refreshTiers()
	{
		fields.refreshTiers();
	}

	void refreshCoxLayout()
	{
		fields.refreshCoxLayout();
	}

	Map<String, String> collectValidatedFields()
	{
		return fields.collectValidatedFields();
	}

	void populate(Map<String, String> values)
	{
		final String raid = values.getOrDefault("raid", "TOB");
		final int raidIndex;
		switch (raid)
		{
			case "COX":
				raidIndex = 1;
				break;
			case "TOA":
				raidIndex = 2;
				break;
			default:
				raidIndex = 0;
		}
		raidCombo.setSelectedIndex(raidIndex);
		raidChosen = true;
		details.setVisible(true);
		refreshRaidTabs();
		fields.populate(values);
	}

	void beginEdit()
	{
		postButton.setText("Save changes");
		cancelEditButton.setVisible(true);
		raidCombo.setEnabled(false);
		fields.setTierEnabled(false);
		setRaidTabsEnabled(false);
	}

	void resetEdit()
	{
		postButton.setText("Post to Discord");
		cancelEditButton.setVisible(false);
		raidCombo.setEnabled(true);
		fields.setTierEnabled(true);
		setRaidTabsEnabled(true);
	}

	void setStatus(String message, boolean error)
	{
		status.setText("<html><body style='width:180px'>" + escapeHtml(message) + "</body></html>");
		status.setForeground(error ? WdrTheme.ERROR : WdrTheme.TEXT_DIM);
	}

	static String generatePartyHub()
	{
		return PartyHubPassphrase.generate();
	}

	private JPanel buildRaidTabs()
	{
		JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
		row.setOpaque(false);
		for (int index = 0; index < raidTabs.length; index++)
		{
			final int tabIndex = index;
			final RaidType raid = index == 0 ? RaidType.TOB : index == 1 ? RaidType.COX : RaidType.TOA;
			raidTabs[index] = new RaidTabButton(raid,
				() -> raidChosen && raidCombo.getSelectedIndex() == tabIndex,
				() -> selectRaidTab(tabIndex));
			row.add(raidTabs[index]);
		}
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return row;
	}

	private void buildActions(Runnable submit, Runnable cancelEdit)
	{
		WdrTheme.styleButton(postButton);
		postButton.addActionListener(e -> submit.run());
		postButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(postButton);
		WdrTheme.styleButton(cancelEditButton);
		cancelEditButton.addActionListener(e -> cancelEdit.run());
		cancelEditButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		cancelEditButton.setVisible(false);
		details.add(cancelEditButton);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(WdrTheme.TEXT_DIM);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(status);
	}

	private RaidType selectedRaid()
	{
		switch (raidCombo.getSelectedIndex())
		{
			case 1:
				return RaidType.COX;
			case 2:
				return RaidType.TOA;
			default:
				return RaidType.TOB;
		}
	}

	private void selectRaidTab(int index)
	{
		raidChosen = true;
		raidCombo.setSelectedIndex(index);
		details.setVisible(true);
		refreshRaidTabs();
		if (dependencies.autoHub().getAsBoolean() && fields.isPartyHubEmpty())
		{
			fields.setPartyHub(generatePartyHub());
		}
		revalidate();
		repaint();
	}

	private void refreshRaidTabs()
	{
		for (RaidTabButton tab : raidTabs)
		{
			tab.refreshSelection();
		}
	}

	private void setRaidTabsEnabled(boolean enabled)
	{
		for (RaidTabButton tab : raidTabs)
		{
			tab.setEnabled(enabled);
		}
	}

	private static String escapeHtml(String value)
	{
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
