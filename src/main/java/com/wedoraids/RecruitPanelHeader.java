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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

final class RecruitPanelHeader extends JPanel
{
	private final WeDoRaidsConfig config;
	private final JLabel statusLabel = new JLabel();
	private final JPanel demoBanner = buildDemoBanner();
	private final BufferedImage logo = loadLogo();
	private BridgeStatus bridgeStatus = BridgeStatus.OFF;
	private int entryCount;

	RecruitPanelHeader(WeDoRaidsConfig config, Runnable onRefresh)
	{
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(WdrTheme.CARD);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, WdrTheme.GREEN),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));

		JLabel title = new JLabel("We Do Raids");
		title.setForeground(WdrTheme.TEXT);
		title.setFont(FontManager.getRunescapeBoldFont());
		JButton refreshButton = new JButton("Refresh");
		WdrTheme.styleButton(refreshButton);
		refreshButton.setToolTipText("Check for new raids now");
		refreshButton.addActionListener(event -> onRefresh.run());

		JPanel titleRow = new JPanel(new BorderLayout(6, 0));
		titleRow.setOpaque(false);
		if (logo != null)
		{
			titleRow.add(new JLabel(new ImageIcon(scale(logo, 40))), BorderLayout.WEST);
		}
		titleRow.add(title, BorderLayout.CENTER);
		titleRow.add(refreshButton, BorderLayout.EAST);
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleRow.getPreferredSize().height));

		JLabel subtitle = new JLabel("Raid recruitment finder");
		subtitle.setForeground(WdrTheme.TEXT_DIM);
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(titleRow);
		add(Box.createVerticalStrut(2));
		add(subtitle);
	}

	JPanel demoBanner()
	{
		return demoBanner;
	}

	JPanel statusBar()
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

	void setBridgeStatus(BridgeStatus status)
	{
		bridgeStatus = status;
		updateStatusBar();
	}

	void setEntryCount(int entryCount)
	{
		this.entryCount = entryCount;
		updateStatusBar();
	}

	void refreshDemoBanner()
	{
		final boolean demo = config.demoData();
		demoBanner.setVisible(demo);
		demoBanner.setMaximumSize(demo
			? new Dimension(Integer.MAX_VALUE, demoBanner.getPreferredSize().height)
			: new Dimension(0, 0));
	}

	BufferedImage logo()
	{
		return logo;
	}

	private void updateStatusBar()
	{
		String text = "● " + bridgeStatus.getLabel();
		if (bridgeStatus == BridgeStatus.ONLINE)
		{
			text += " · " + entryCount + " open";
		}
		statusLabel.setText(text);
		statusLabel.setForeground(bridgeStatus.getColor());
	}

	private static JPanel buildDemoBanner()
	{
		final JPanel banner = new JPanel(new BorderLayout());
		banner.setBackground(WdrTheme.CARD);
		banner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.ERROR),
			BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		final JLabel label = new JLabel("DEMO MODE: sample calls, not live");
		label.setForeground(WdrTheme.ERROR);
		label.setFont(FontManager.getRunescapeSmallFont());
		banner.add(label, BorderLayout.CENTER);
		return banner;
	}

	private static BufferedImage loadLogo()
	{
		try
		{
			return ImageUtil.loadImageResource(WeDoRaidsPanel.class, "wdr.png");
		}
		catch (RuntimeException exception)
		{
			return null;
		}
	}

	private static Image scale(BufferedImage image, int height)
	{
		int width = Math.max(1, image.getWidth() * height / image.getHeight());
		return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}
}
