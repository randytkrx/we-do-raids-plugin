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
package com.wedoraids.panel;

import com.wedoraids.feed.RaidType;
import com.wedoraids.feed.RecruitEntry;
import com.wedoraids.ui.WdrScrollBarUI;
import com.wedoraids.ui.WdrTheme;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

final class RecruitListPanel extends JPanel
{
	private static final int MAX_ENTRIES = 50;

	private final List<RecruitEntry> entries = new ArrayList<>();
	private final RecruitFilterBar filterBar;
	private final IntConsumer onHopWorld;
	private final Consumer<String> onJoinHub;
	private final IntConsumer onEntryCountChanged;
	private final BufferedImage logo;
	private final JLabel countLabel = new JLabel();
	private boolean banned;
	private boolean verified;
	private boolean loggedIn = true;

	RecruitListPanel(RecruitFilterBar filterBar, IntConsumer onHopWorld, Consumer<String> onJoinHub,
		IntConsumer onEntryCountChanged, BufferedImage logo)
	{
		this.filterBar = filterBar;
		this.onHopWorld = onHopWorld;
		this.onJoinHub = onJoinHub;
		this.onEntryCountChanged = onEntryCountChanged;
		this.logo = logo;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		countLabel.setFont(FontManager.getRunescapeSmallFont());
	}

	JLabel countLabel()
	{
		return countLabel;
	}

	JScrollPane scrollPane()
	{
		JScrollPane scroll = new JScrollPane(this,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setBackground(WdrTheme.BACKGROUND);
		scroll.getViewport().setBackground(WdrTheme.BACKGROUND);
		scroll.getVerticalScrollBar().setUI(new WdrScrollBarUI());
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	void setBanned(boolean banned)
	{
		if (this.banned == banned)
		{
			return;
		}
		this.banned = banned;
		if (banned)
		{
			entries.clear();
		}
		rebuild();
	}

	void setVerified(boolean verified)
	{
		this.verified = verified;
		if (!verified)
		{
			entries.clear();
		}
		rebuild();
	}

	void setLoggedIn(boolean loggedIn)
	{
		if (this.loggedIn == loggedIn)
		{
			return;
		}
		this.loggedIn = loggedIn;
		if (!loggedIn)
		{
			entries.clear();
		}
		rebuild();
	}

	void setEntries(List<RecruitEntry> newEntries)
	{
		entries.clear();
		if (!banned && verified && loggedIn)
		{
			for (RecruitEntry entry : newEntries)
			{
				if (entries.size() >= MAX_ENTRIES)
				{
					break;
				}
				entries.add(entry);
			}
		}
		rebuild();
	}

	void clear()
	{
		entries.clear();
		rebuild();
	}

	void rebuild()
	{
		removeAll();
		updateCounts();
		onEntryCountChanged.accept(entries.size());

		if (!loggedIn)
		{
			add(buildNotice("Log in",
				"Log in on your verified We Do Raids account to see and host raids."));
		}
		else if (banned)
		{
			add(buildNotice("Recruitment hidden",
				"This account is on the We Do Raids ban list, so recruiting calls are hidden and you can't host."));
		}
		else if (!verified)
		{
			add(buildNotice("Re-auth please",
				"Re-authenticate on the We Do Raids Discord: run !verify in #auth, then paste your key into the "
					+ "Verification key setting. Your logged-in RSN must match your WDR nickname."));
		}
		else
		{
			final List<RecruitEntry> visible = visibleEntries();
			if (visible.isEmpty())
			{
				final boolean filtered = !entries.isEmpty();
				add(buildNotice(
					filtered ? "No matches" : "Waiting for calls",
					filtered ? "No open raids match this filter. Try 'All raids' / 'All tiers'."
						: "Recruiting calls from the We Do Raids Discord will appear here."));
			}
			else
			{
				for (RecruitEntry entry : visible)
				{
					add(new RecruitEntryPanel(entry, onHopWorld, onJoinHub));
					add(Box.createVerticalStrut(6));
				}
			}
		}

		revalidate();
		repaint();
	}

	private List<RecruitEntry> visibleEntries()
	{
		final RaidType raid = filterBar.selectedRaid();
		final String tier = filterBar.selectedTier();
		final List<RecruitEntry> visible = new ArrayList<>();
		for (RecruitEntry entry : entries)
		{
			if (raid != null && entry.getRaidType() != raid)
			{
				continue;
			}
			if (tier != null && !tier.equalsIgnoreCase(entry.getTier()))
			{
				continue;
			}
			visible.add(entry);
		}
		return visible;
	}

	private void updateCounts()
	{
		countLabel.setText("<html>"
			+ chip(RaidType.TOB, countEntries(RaidType.TOB)) + "&nbsp;&nbsp;"
			+ chip(RaidType.COX, countEntries(RaidType.COX)) + "&nbsp;&nbsp;"
			+ chip(RaidType.TOA, countEntries(RaidType.TOA)) + "</html>");
	}

	private JPanel buildNotice(String title, String description)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(WdrTheme.CARD);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.BORDER),
			BorderFactory.createEmptyBorder(18, 12, 18, 12)));
		if (logo != null)
		{
			JLabel logoLabel = new JLabel(new ImageIcon(scale(logo, 52)));
			logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			panel.add(logoLabel);
			panel.add(Box.createVerticalStrut(10));
		}
		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(WdrTheme.TEXT);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(titleLabel);
		panel.add(Box.createVerticalStrut(4));
		JLabel descLabel = new JLabel("<html><div style='text-align:center;width:150px'>"
			+ escapeHtml(description) + "</div></html>");
		descLabel.setForeground(WdrTheme.TEXT_DIM);
		descLabel.setFont(FontManager.getRunescapeSmallFont());
		descLabel.setHorizontalAlignment(SwingConstants.CENTER);
		descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(descLabel);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private static String chip(RaidType raid, int count)
	{
		return "<span style='color:" + hex(raid.getColor()) + "'>"
			+ raid.getDisplayName() + " " + count + "</span>";
	}

	private int countEntries(RaidType raid)
	{
		return (int) entries.stream().filter(entry -> entry.getRaidType() == raid).count();
	}

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static Image scale(BufferedImage image, int height)
	{
		int width = Math.max(1, image.getWidth() * height / image.getHeight());
		return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}

	private static String hex(java.awt.Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
}
