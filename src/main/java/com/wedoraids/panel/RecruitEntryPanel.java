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

import com.wedoraids.feed.RecruitDisplay;
import com.wedoraids.feed.RecruitEntry;
import com.wedoraids.ui.WdrTheme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

final class RecruitEntryPanel extends JPanel
{
	RecruitEntryPanel(RecruitEntry entry, IntConsumer onHopWorld, Consumer<String> onJoinHub)
	{
		super(new BorderLayout(0, 4));
		setBackground(WdrTheme.CARD);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.BORDER),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, entry.getRaidType().getColor()),
				BorderFactory.createEmptyBorder(6, 8, 6, 8))));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setToolTipText(buildToolTip(entry));

		String raidText = entry.getRaidType().getDisplayName();
		final String badge = entry.getTier() != null ? entry.getTier() : entry.getMode();
		if (badge != null)
		{
			raidText += " " + badge;
		}
		JLabel raidLabel = new JLabel(raidText);
		raidLabel.setForeground(entry.getRaidType().getColor());
		raidLabel.setFont(FontManager.getRunescapeBoldFont());

		JLabel worldLabel = buildWorldLabel(entry, onHopWorld);
		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setOpaque(false);
		topRow.add(raidLabel, BorderLayout.WEST);
		topRow.add(worldLabel, BorderLayout.EAST);

		StringBuilder detail = buildDetail(entry);
		JLabel messageLabel = new JLabel("<html><body style='width:150px'>"
			+ escapeHtml(entry.getMessage()) + "</body></html>");
		messageLabel.setForeground(WdrTheme.TEXT_DIM);
		messageLabel.setFont(FontManager.getRunescapeSmallFont());

		JLabel senderLabel = buildSenderLabel(entry);
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
		addHubLabel(body, entry, onJoinHub);

		add(topRow, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		add(bottomRow, BorderLayout.SOUTH);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
	}

	private static String buildToolTip(RecruitEntry entry)
	{
		StringBuilder tip = new StringBuilder("<html><b>" + escapeHtml(entry.getSender()) + "</b> ("
			+ escapeHtml(entry.getSource()) + ")");
		if (entry.getHost() != null)
		{
			tip.append("<br>Party hub: ").append(escapeHtml(entry.getHost()));
		}
		return tip.append("<br>").append(escapeHtml(entry.getMessage())).append("</html>").toString();
	}

	private static JLabel buildWorldLabel(RecruitEntry entry, IntConsumer onHopWorld)
	{
		JLabel worldLabel = new JLabel(entry.getWorld() != 0 ? "W" + entry.getWorld() : "");
		worldLabel.setForeground(WdrTheme.TEXT);
		worldLabel.setFont(FontManager.getRunescapeSmallFont());
		if (entry.getWorld() != 0)
		{
			worldLabel.setToolTipText("Click to hop to world " + entry.getWorld());
			worldLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			worldLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					onHopWorld.accept(entry.getWorld());
				}

				@Override
				public void mouseEntered(MouseEvent event)
				{
					worldLabel.setForeground(java.awt.Color.WHITE);
				}

				@Override
				public void mouseExited(MouseEvent event)
				{
					worldLabel.setForeground(WdrTheme.TEXT);
				}
			});
		}
		return worldLabel;
	}

	private static StringBuilder buildDetail(RecruitEntry entry)
	{
		StringBuilder detail = new StringBuilder();
		if (entry.getTier() != null)
		{
			appendDetail(detail, entry.getMode());
		}
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
		return detail;
	}

	private static JLabel buildSenderLabel(RecruitEntry entry)
	{
		JLabel senderLabel = new JLabel("<html>" + escapeHtml(entry.getSender() + " · " + entry.getSource()) + "</html>");
		senderLabel.setForeground(WdrTheme.TEXT);
		senderLabel.setFont(FontManager.getRunescapeSmallFont());
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
		return senderLabel;
	}

	private static void addHubLabel(JPanel body, RecruitEntry entry, Consumer<String> onJoinHub)
	{
		if (entry.getHost() == null || entry.getHost().trim().isEmpty())
		{
			return;
		}
		final String hub = entry.getHost().trim();
		final JLabel hubLabel = new JLabel("ph: " + hub + " - click to join");
		hubLabel.setForeground(WdrTheme.GREEN);
		hubLabel.setFont(FontManager.getRunescapeSmallFont());
		hubLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		hubLabel.setToolTipText("Join party \"" + escapeHtml(hub) + "\" in the RuneLite Party plugin");
		hubLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hubLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				onJoinHub.accept(hub);
				hubLabel.setText("ph: " + hub + " - joined");
			}

			@Override
			public void mouseEntered(MouseEvent event)
			{
				hubLabel.setForeground(WdrTheme.GREEN_BRIGHT);
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hubLabel.setForeground(WdrTheme.GREEN);
			}
		});
		body.add(hubLabel);
	}

	private static void appendDetail(StringBuilder detail, String value)
	{
		if (value == null || value.isEmpty())
		{
			return;
		}
		if (detail.length() > 0)
		{
			detail.append("  ");
		}
		detail.append(value);
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

	private static String escapeHtml(String value)
	{
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
