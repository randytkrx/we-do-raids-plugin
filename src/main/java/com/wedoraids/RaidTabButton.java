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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import net.runelite.client.ui.FontManager;

final class RaidTabButton extends JButton
{
	private final RaidType raid;
	private final BooleanSupplier selected;

	RaidTabButton(RaidType raid, BooleanSupplier selected, Runnable onSelected)
	{
		super(raid.getDisplayName());
		this.raid = raid;
		this.selected = selected;
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(WdrTheme.TEXT_DIM);
		setFocusPainted(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setOpaque(false);
		setCursor(new Cursor(Cursor.HAND_CURSOR));
		setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		addActionListener(e -> onSelected.run());
	}

	void refreshSelection()
	{
		setForeground(selected.getAsBoolean() ? raid.getColor() : WdrTheme.TEXT_DIM);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		final Graphics2D graphics2d = (Graphics2D) graphics.create();
		graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final boolean isSelected = selected.getAsBoolean();
		final Color raidColor = raid.getColor();
		final Color fill;
		if (isSelected)
		{
			fill = new Color(raidColor.getRed() / 5 + 18, raidColor.getGreen() / 5 + 18,
				raidColor.getBlue() / 5 + 18);
		}
		else if (getModel().isRollover())
		{
			fill = WdrTheme.HOVER;
		}
		else
		{
			fill = WdrTheme.FIELD;
		}
		graphics2d.setColor(fill);
		graphics2d.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
		graphics2d.setColor(isSelected ? raidColor : WdrTheme.BORDER);
		graphics2d.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
		graphics2d.dispose();
		super.paintComponent(graphics);
	}
}
