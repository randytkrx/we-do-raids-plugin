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
import java.util.function.BiConsumer;
import javax.swing.JComboBox;
import javax.swing.JPanel;

final class RecruitFilterBar extends JPanel
{
	private final WeDoRaidsConfig config;
	private final BiConsumer<String, String> saveFilter;
	private final Runnable onFilterChanged;
	private final JComboBox<String> raidFilter = new JComboBox<>(new String[]{"All raids", "ToB", "CoX", "ToA"});
	private final JComboBox<String> tierFilter = new JComboBox<>(new String[]{"All tiers"});
	private boolean restoring;

	RecruitFilterBar(WeDoRaidsConfig config, BiConsumer<String, String> saveFilter, Runnable onFilterChanged)
	{
		super(new GridLayout(1, 2, 4, 0));
		this.config = config;
		this.saveFilter = saveFilter;
		this.onFilterChanged = onFilterChanged;

		WdrTheme.styleCombo(raidFilter);
		WdrTheme.styleCombo(tierFilter);
		raidFilter.addActionListener(event ->
		{
			refreshTierOptions();
			if (!restoring)
			{
				saveFilter.accept("lastRaidFilter", String.valueOf(raidFilter.getSelectedItem()));
			}
			onFilterChanged.run();
		});
		tierFilter.addActionListener(event ->
		{
			if (!restoring)
			{
				saveFilter.accept("lastTierFilter", String.valueOf(tierFilter.getSelectedItem()));
			}
			onFilterChanged.run();
		});

		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		add(raidFilter);
		add(tierFilter);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, raidFilter.getPreferredSize().height));
	}

	void restoreSelection()
	{
		restoring = true;
		try
		{
			raidFilter.setSelectedItem(config.lastRaidFilter());
			refreshTierOptions();
			final String tier = config.lastTierFilter();
			for (int i = 0; i < tierFilter.getItemCount(); i++)
			{
				if (tierFilter.getItemAt(i).equals(tier))
				{
					tierFilter.setSelectedIndex(i);
					break;
				}
			}
		}
		finally
		{
			restoring = false;
		}
	}

	RaidType selectedRaid()
	{
		switch (raidFilter.getSelectedIndex())
		{
			case 1:
				return RaidType.TOB;
			case 2:
				return RaidType.COX;
			case 3:
				return RaidType.TOA;
			default:
				return null;
		}
	}

	String selectedTier()
	{
		final Object selection = tierFilter.getSelectedItem();
		if (selection == null || "All tiers".equals(selection))
		{
			return null;
		}
		return selection.toString();
	}

	private void refreshTierOptions()
	{
		final RaidType raid = selectedRaid();
		tierFilter.removeAllItems();
		tierFilter.addItem("All tiers");
		if (raid != null)
		{
			for (String tier : raid.getTiers())
			{
				tierFilter.addItem(tier);
			}
		}
	}
}
