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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

class WeDoRaidsPanel extends PluginPanel
{
	private static final int MAX_ENTRIES = 50;

	private final WeDoRaidsConfig config;
	private final List<RecruitEntry> entries = new ArrayList<>();

	private final JPanel listPanel = new JPanel();
	private final JComboBox<String> raidFilter = new JComboBox<>(new String[]{"All raids", "ToB", "CoX", "ToA"});
	private final JComboBox<String> tierFilter = new JComboBox<>(new String[]{"All tiers"});
	private final JLabel countLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final BufferedImage logo = loadLogo();
	private HostFormPanel hostForm;
	private final java.util.function.BiConsumer<String, String> saveFilter;
	private final java.util.function.IntConsumer onHopWorld;
	private final java.util.function.Consumer<String> onJoinHub;
	private final Runnable onRefresh;

	private boolean banned;
	private boolean verified;
	private boolean loggedIn = true;
	private boolean restoring;
	private BridgeStatus bridgeStatus = BridgeStatus.OFF;

	WeDoRaidsPanel(WeDoRaidsConfig config, HostFormPanel.HostActions hostActions,
		java.util.function.BiConsumer<String, String> saveFilter,
		java.util.function.IntConsumer onHopWorld, java.util.function.IntSupplier currentWorld,
		java.util.function.Supplier<String> coxLayout, java.util.function.Supplier<String> localIgn,
		java.util.function.ToIntFunction<RaidType> userKc, Runnable requestKc, Runnable onIdleWarn,
		java.util.function.BooleanSupplier autoHub, java.util.function.Consumer<String> onJoinHub,
		Runnable onRefresh, java.util.function.IntFunction<String> worldBlockReason)
	{
		// Manage our own scrolling so the Olm header/filters stay fixed and only the list scrolls.
		super(false);
		this.config = config;
		this.saveFilter = saveFilter;
		this.onHopWorld = onHopWorld;
		this.onJoinHub = onJoinHub;
		this.onRefresh = onRefresh;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(WdrTheme.BACKGROUND);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(buildHeader());
		top.add(Box.createVerticalStrut(6));

		// Host form sits up top, above the filters, so hosting is the first action.
		hostForm = new HostFormPanel(hostActions, currentWorld, coxLayout, localIgn, userKc, requestKc, onIdleWarn, autoHub, worldBlockReason);
		hostForm.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(hostForm);
		top.add(Box.createVerticalStrut(6));

		top.add(divider());
		top.add(Box.createVerticalStrut(6));
		top.add(buildFilterBar());
		restoreFilterSelection();
		top.add(Box.createVerticalStrut(4));

		countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		countLabel.setFont(FontManager.getRunescapeSmallFont());
		top.add(countLabel);
		top.add(Box.createVerticalStrut(6));

		add(top, BorderLayout.NORTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setOpaque(false);

		JScrollPane scroll = new JScrollPane(listPanel,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.setBackground(WdrTheme.BACKGROUND);
		scroll.getViewport().setBackground(WdrTheme.BACKGROUND);
		scroll.getVerticalScrollBar().setUI(new WdrScrollBarUI());
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(9, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		add(buildStatusBar(), BorderLayout.SOUTH);

		updateStatusBar();
		rebuild();
	}

	private JPanel buildStatusBar()
	{
		statusLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, WdrTheme.BORDER),
			BorderFactory.createEmptyBorder(5, 2, 0, 2)));
		bar.add(statusLabel, BorderLayout.WEST);
		return bar;
	}

	/** Reflects whether the Discord bridge is reachable. Must be called on the EDT. */
	void setBridgeStatus(BridgeStatus status)
	{
		this.bridgeStatus = status;
		updateStatusBar();
	}

	private void updateStatusBar()
	{
		String text = "● " + bridgeStatus.getLabel();
		if (bridgeStatus == BridgeStatus.ONLINE)
		{
			text += " · " + entries.size() + " open";
		}
		statusLabel.setText(text);
		statusLabel.setForeground(bridgeStatus.getColor());
	}

	private JPanel buildHeader()
	{
		JLabel title = new JLabel("We Do Raids");
		title.setForeground(WdrTheme.TEXT);
		title.setFont(FontManager.getRunescapeBoldFont());

		JButton refreshButton = new JButton("Refresh");
		WdrTheme.styleButton(refreshButton);
		refreshButton.setToolTipText("Check for new raids now");
		refreshButton.addActionListener(e -> onRefresh.run());

		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setOpaque(false);
		if (logo != null)
		{
			titleRow.add(new JLabel(new ImageIcon(scale(logo, 40))), BorderLayout.WEST);
		}
		titleRow.add(title, BorderLayout.CENTER);
		titleRow.add(refreshButton, BorderLayout.EAST);

		// Subtitle on its own full-width line so it isn't squeezed/truncated.
		JLabel subtitle = new JLabel("Raid recruitment finder");
		subtitle.setForeground(WdrTheme.TEXT_DIM);
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleRow.getPreferredSize().height));

		// Olm banner: dark card with a green underline accent.
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(WdrTheme.CARD);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, WdrTheme.GREEN),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));
		header.add(titleRow);
		header.add(Box.createVerticalStrut(2));
		header.add(subtitle);
		return header;
	}

	private JPanel buildFilterBar()
	{
		WdrTheme.styleCombo(raidFilter);
		WdrTheme.styleCombo(tierFilter);
		raidFilter.addActionListener(e ->
		{
			refreshTierFilterOptions();
			if (!restoring)
			{
				saveFilter.accept("lastRaidFilter", String.valueOf(raidFilter.getSelectedItem()));
			}
			rebuild();
		});
		tierFilter.addActionListener(e ->
		{
			if (!restoring)
			{
				saveFilter.accept("lastTierFilter", String.valueOf(tierFilter.getSelectedItem()));
			}
			rebuild();
		});

		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		row.add(raidFilter);
		row.add(tierFilter);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, raidFilter.getPreferredSize().height));
		return row;
	}

	private void refreshTierFilterOptions()
	{
		final RaidType raid = filterRaid();
		tierFilter.removeAllItems();
		tierFilter.addItem("All tiers");
		if (raid != null)
		{
			for (String t : raid.getTiers())
			{
				tierFilter.addItem(t);
			}
		}
	}

	/** Restore the raid/tier selection saved from a previous session. */
	private void restoreFilterSelection()
	{
		restoring = true;
		try
		{
			raidFilter.setSelectedItem(config.lastRaidFilter());
			refreshTierFilterOptions();
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

	private JPanel divider()
	{
		JPanel line = new JPanel();
		line.setBackground(WdrTheme.BORDER);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}

	/**
	 * When banned, the panel refuses to show any recruitments. Must be called on the EDT.
	 */
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

	/** When unverified, the panel hides all recruitments and shows how to verify. */
	void setVerified(boolean verified)
	{
		if (this.verified == verified)
		{
			return;
		}
		this.verified = verified;
		if (!verified)
		{
			entries.clear();
		}
		rebuild();
	}

	/** Re-filters the host form's tier dropdown once the player's KC has loaded. */
	void refreshHostTiers()
	{
		if (hostForm != null)
		{
			hostForm.refreshTiersPublic();
		}
	}

	/** Pushes the newest scouted CoX layout into the host form (on a re-scout). */
	void refreshCoxLayout()
	{
		if (hostForm != null)
		{
			hostForm.refreshCoxLayout();
		}
	}

	/** After a successful host, switch the form into live edit mode for that message. */
	void enterHostLive(String messageId)
	{
		if (hostForm != null)
		{
			hostForm.enterLivePost(messageId);
		}
	}

	/** After closing a raid, restore the normal host form. */
	void exitHostLive()
	{
		if (hostForm != null)
		{
			hostForm.exitLivePost();
		}
	}

	/** Plugin shutdown: stop the host form's timers so nothing fires into a dead panel. */
	void shutdown()
	{
		if (hostForm != null)
		{
			hostForm.exitLivePost();
			hostForm.stopTimers();
		}
	}

	/** Logged-out players get a log-in prompt instead of stale raids. */
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

	/**
	 * Adds an entry, replacing any earlier call from the same sender for the same raid.
	 * Used by demo mode. Must be called on the EDT.
	 */
	void addEntry(RecruitEntry entry)
	{
		if (banned || !verified || !loggedIn)
		{
			return;
		}
		entries.removeIf(e -> e.getSender().equalsIgnoreCase(entry.getSender())
			&& e.getRaidType() == entry.getRaidType());
		entries.add(0, entry);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.remove(entries.size() - 1);
		}
		rebuild();
	}

	/**
	 * Replaces the whole list with the bridge's current feed (the source of truth), so
	 * deleted/closed raids disappear. Must be called on the EDT.
	 */
	void setEntries(List<RecruitEntry> newEntries)
	{
		entries.clear();
		if (!banned && verified && loggedIn)
		{
			for (RecruitEntry e : newEntries)
			{
				if (entries.size() >= MAX_ENTRIES)
				{
					break;
				}
				entries.add(e);
			}
		}
		rebuild();
	}

	void clear()
	{
		entries.clear();
		rebuild();
	}

	private RaidType filterRaid()
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

	private String filterTier()
	{
		final Object sel = tierFilter.getSelectedItem();
		if (sel == null || "All tiers".equals(sel))
		{
			return null;
		}
		return sel.toString();
	}

	private List<RecruitEntry> visibleEntries()
	{
		final RaidType raid = filterRaid();
		final String tier = filterTier();
		final List<RecruitEntry> out = new ArrayList<>();
		for (RecruitEntry e : entries)
		{
			if (raid != null && e.getRaidType() != raid)
			{
				continue;
			}
			if (tier != null && !tier.equalsIgnoreCase(e.getTier()))
			{
				continue;
			}
			out.add(e);
		}
		return out;
	}

	private void updateCounts()
	{
		int tob = 0;
		int cox = 0;
		int toa = 0;
		for (RecruitEntry e : entries)
		{
			switch (e.getRaidType())
			{
				case TOB:
					tob++;
					break;
				case COX:
					cox++;
					break;
				case TOA:
					toa++;
					break;
				default:
					break;
			}
		}
		countLabel.setText("<html>"
			+ chip(RaidType.TOB, tob) + "&nbsp;&nbsp;"
			+ chip(RaidType.COX, cox) + "&nbsp;&nbsp;"
			+ chip(RaidType.TOA, toa) + "</html>");
	}

	private static String chip(RaidType raid, int n)
	{
		return "<span style='color:" + hex(raid.getColor()) + "'>"
			+ raid.getDisplayName() + " " + n + "</span>";
	}

	private void rebuild()
	{
		listPanel.removeAll();
		updateCounts();
		updateStatusBar();

		if (!loggedIn)
		{
			listPanel.add(buildNotice("Log in",
				"Log in on your verified We Do Raids account to see and host raids."));
		}
		else if (banned)
		{
			listPanel.add(buildNotice("Recruitment hidden",
				"This account is on the We Do Raids ban list, so recruiting calls are hidden and you can't host."));
		}
		else if (!verified)
		{
			listPanel.add(buildNotice("Re-auth please",
				"Re-authenticate on the We Do Raids Discord: run !verify in #auth, then paste your key into the "
					+ "Verification key setting. Your logged-in RSN must match your WDR nickname."));
		}
		else
		{
			final List<RecruitEntry> visible = visibleEntries();
			if (visible.isEmpty())
			{
				final boolean filtered = !entries.isEmpty();
				listPanel.add(buildNotice(
					filtered ? "No matches" : "Waiting for calls",
					filtered ? "No open raids match this filter — try 'All raids' / 'All tiers'."
						: "Recruiting calls from the We Do Raids Discord will appear here."));
			}
			else
			{
				for (RecruitEntry entry : visible)
				{
					listPanel.add(buildEntryPanel(entry));
					listPanel.add(Box.createVerticalStrut(6));
				}
			}
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private JPanel buildEntryPanel(RecruitEntry entry)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setBackground(WdrTheme.CARD);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.BORDER),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, entry.getRaidType().getColor()),
				BorderFactory.createEmptyBorder(6, 8, 6, 8))));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		StringBuilder tip = new StringBuilder("<html><b>" + escapeHtml(entry.getSender()) + "</b> ("
			+ escapeHtml(entry.getSource()) + ")");
		if (entry.getHost() != null)
		{
			tip.append("<br>Party hub: ").append(escapeHtml(entry.getHost()));
		}
		tip.append("<br>").append(escapeHtml(entry.getMessage())).append("</html>");
		panel.setToolTipText(tip.toString());

		// Prefer the WDR tier next to the raid name (it's the KC-gated bit that matters);
		// fall back to mode when there's no tier (ToA/CoX).
		String raidText = entry.getRaidType().getDisplayName();
		final String badge = entry.getTier() != null ? entry.getTier() : entry.getMode();
		if (badge != null)
		{
			raidText += " " + badge;
		}
		JLabel raidLabel = new JLabel(raidText);
		raidLabel.setForeground(entry.getRaidType().getColor());
		raidLabel.setFont(FontManager.getRunescapeBoldFont());

		JLabel worldLabel = new JLabel(entry.getWorld() != 0 ? "W" + entry.getWorld() : "");
		worldLabel.setForeground(WdrTheme.TEXT);
		worldLabel.setFont(FontManager.getRunescapeSmallFont());
		if (entry.getWorld() != 0)
		{
			// Click the world to quick-hop there.
			worldLabel.setToolTipText("Click to hop to world " + entry.getWorld());
			worldLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			worldLabel.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent e)
				{
					onHopWorld.accept(entry.getWorld());
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					worldLabel.setForeground(java.awt.Color.WHITE);
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					worldLabel.setForeground(WdrTheme.TEXT);
				}
			});
		}

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setOpaque(false);
		topRow.add(raidLabel, BorderLayout.WEST);
		topRow.add(worldLabel, BorderLayout.EAST);

		// Detail line: mode (only if a tier already took the badge slot), spots, roles, duo/trio, region.
		StringBuilder detail = new StringBuilder();
		if (entry.getTier() != null)
		{
			appendDetail(detail, entry.getMode());
		}
		// Prefer a clear "here/total" fill (e.g. 3/5) when we know both the party size and
		// the open spots; otherwise fall back to showing the raw "+2" and duo/trio word.
		final int total = RecruitDisplay.teamTotal(entry.getPartySize());
		final int open = RecruitDisplay.firstInt(entry.getSpots());
		if (total > 0 && open > 0)
		{
			appendDetail(detail, Math.max(0, total - open) + "/" + total);
		}
		else
		{
			appendDetail(detail, entry.getSpots());
			appendDetail(detail, entry.getPartySize());
		}
		appendDetail(detail, entry.getRoles());
		appendDetail(detail, entry.getRegion() != null ? entry.getRegion().toUpperCase() : null);

		JLabel messageLabel = new JLabel("<html><body style='width:150px'>"
			+ escapeHtml(entry.getMessage()) + "</body></html>");
		messageLabel.setForeground(WdrTheme.TEXT_DIM);
		messageLabel.setFont(FontManager.getRunescapeSmallFont());

		// Escaped + html-wrapped: a Discord name starting with "<html>" would otherwise
		// make Swing render attacker HTML (which can even fetch remote images).
		JLabel senderLabel = new JLabel("<html>" + escapeHtml(entry.getSender() + " · " + entry.getSource()) + "</html>");
		senderLabel.setForeground(WdrTheme.TEXT);
		senderLabel.setFont(FontManager.getRunescapeSmallFont());
		// Hover the IGN to see the poster's own raid KC and the WDR tier that KC unlocks
		// (not the channel's tier — that's already the badge on the card).
		StringBuilder who = new StringBuilder("<html><b>" + escapeHtml(entry.getSender()) + "</b>");
		who.append("<br>").append(escapeHtml(entry.getRaidType().getDisplayName())).append(" KC: ")
			.append(entry.getKc() > 0 ? String.valueOf(entry.getKc()) : "unknown");
		final String personTier = RecruitDisplay.wdrTierNumber(entry.getRaidType(), entry.getKc());
		if (personTier != null)
		{
			who.append("<br>Their WDR tier: ").append(personTier);
		}
		who.append("</html>");
		senderLabel.setToolTipText(who.toString());

		JLabel timeLabel = new JLabel(timeAgo(entry.getTimestamp()));
		timeLabel.setForeground(WdrTheme.TEXT_DIM);
		timeLabel.setFont(FontManager.getRunescapeSmallFont());

		JPanel bottomRow = new JPanel(new BorderLayout());
		bottomRow.setOpaque(false);
		bottomRow.add(senderLabel, BorderLayout.WEST);
		bottomRow.add(timeLabel, BorderLayout.EAST);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		if (detail.length() > 0)
		{
			JLabel detailLabel = new JLabel(detail.toString());
			detailLabel.setForeground(entry.getRaidType().getColor());
			detailLabel.setFont(FontManager.getRunescapeSmallFont());
			detailLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			body.add(detailLabel);
		}
		messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(messageLabel);

		// Clickable party hub on its own line: joins the party via the Party plugin.
		if (entry.getHost() != null && !entry.getHost().trim().isEmpty())
		{
			final String hub = entry.getHost().trim();
			final JLabel hubLabel = new JLabel("ph: " + hub + "  — click to join");
			hubLabel.setForeground(WdrTheme.GREEN);
			hubLabel.setFont(FontManager.getRunescapeSmallFont());
			hubLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			hubLabel.setToolTipText("Join party \"" + escapeHtml(hub) + "\" in the RuneLite Party plugin");
			hubLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			hubLabel.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mousePressed(java.awt.event.MouseEvent e)
				{
					onJoinHub.accept(hub);
					hubLabel.setText("ph: " + hub + "  — joined");
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					hubLabel.setForeground(WdrTheme.GREEN_BRIGHT);
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					hubLabel.setForeground(WdrTheme.GREEN);
				}
			});
			body.add(hubLabel);
		}

		panel.add(topRow, BorderLayout.NORTH);
		panel.add(body, BorderLayout.CENTER);
		panel.add(bottomRow, BorderLayout.SOUTH);
		// BoxLayout stretches children vertically unless the max height is pinned
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private static void appendDetail(StringBuilder sb, String value)
	{
		if (value == null || value.isEmpty())
		{
			return;
		}
		if (sb.length() > 0)
		{
			sb.append("  ");
		}
		sb.append(value);
	}

	private static String timeAgo(Instant timestamp)
	{
		long minutes = Duration.between(timestamp, Instant.now()).toMinutes();
		if (minutes < 1)
		{
			return "now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m ago";
	}

	private static String escapeHtml(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** A themed empty/banned notice with the WDR logo, so the sidebar never looks blank. */
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

	private static BufferedImage loadLogo()
	{
		try
		{
			return ImageUtil.loadImageResource(WeDoRaidsPanel.class, "wdr.png");
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static Image scale(BufferedImage image, int height)
	{
		int width = Math.max(1, image.getWidth() * height / image.getHeight());
		return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}

	private static String hex(java.awt.Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
