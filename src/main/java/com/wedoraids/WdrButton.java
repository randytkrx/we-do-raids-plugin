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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import net.runelite.client.ui.FontManager;

/**
 * A rounded, hover-aware pill button in the WDR palette. Three variants:
 * PRIMARY (filled green, a positive action), GHOST (outlined, a soft action) and
 * DANGER (red, a destructive action). Painted by hand so it looks nothing like the
 * default Swing button.
 */
class WdrButton extends JButton
{
	enum Variant
	{
		PRIMARY, GHOST, DANGER
	}

	private final Variant variant;
	private boolean hover;

	WdrButton(String text, Variant variant)
	{
		super(text);
		this.variant = variant;
		setFont(FontManager.getRunescapeSmallFont());
		setFocusPainted(false);
		setContentAreaFilled(false);
		setBorderPainted(false);
		setOpaque(false);
		setForeground(textColor());
		setCursor(new Cursor(Cursor.HAND_CURSOR));
		setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				setForeground(textColor());
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				setForeground(textColor());
				repaint();
			}
		});
	}

	private Color textColor()
	{
		switch (variant)
		{
			case PRIMARY:
				return WdrTheme.BACKGROUND; // dark text on a bright green pill
			case DANGER:
				return hover ? new Color(255, 225, 225) : new Color(240, 180, 180);
			case GHOST:
			default:
				return hover ? Color.WHITE : WdrTheme.TEXT;
		}
	}

	private Color fillColor()
	{
		final boolean pressed = getModel().isArmed() && getModel().isPressed();
		switch (variant)
		{
			case PRIMARY:
				return pressed ? WdrTheme.GREEN_DIM : (hover ? WdrTheme.GREEN_BRIGHT : WdrTheme.GREEN);
			case DANGER:
				return pressed ? new Color(120, 44, 44)
					: (hover ? new Color(112, 44, 44) : new Color(70, 32, 32));
			case GHOST:
			default:
				return pressed ? WdrTheme.BORDER
					: (hover ? WdrTheme.HOVER : WdrTheme.FIELD);
		}
	}

	private Color outlineColor()
	{
		switch (variant)
		{
			case PRIMARY:
				return null;
			case DANGER:
				return new Color(150, 70, 70);
			case GHOST:
			default:
				return WdrTheme.BORDER;
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int w = getWidth();
		final int h = getHeight();
		final int arc = h; // fully rounded pill ends

		g2.setColor(fillColor());
		g2.fillRoundRect(0, 0, w, h, arc, arc);
		final Color outline = outlineColor();
		if (outline != null)
		{
			g2.setColor(outline);
			g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
		}
		g2.dispose();
		super.paintComponent(g);
	}
}
