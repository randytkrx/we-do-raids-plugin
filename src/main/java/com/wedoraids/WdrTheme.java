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
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * We Do Raids palette. Structure (backgrounds, borders, text) uses RuneLite's native
 * {@link ColorScheme} so the panel blends in with the rest of the client; a muted WDR
 * green is kept as the accent for brand identity.
 */
final class WdrTheme
{
	static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color FIELD = ColorScheme.DARKER_GRAY_COLOR;
	static final Color HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
	static final Color GREEN = new Color(86, 171, 106);
	static final Color GREEN_BRIGHT = new Color(126, 201, 145);
	static final Color GREEN_DIM = new Color(48, 84, 58);
	static final Color BORDER = ColorScheme.MEDIUM_GRAY_COLOR;
	static final Color TEXT = new Color(220, 220, 220);
	static final Color TEXT_DIM = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color ERROR = ColorScheme.PROGRESS_ERROR_COLOR;

	private WdrTheme()
	{
	}

	static void styleButton(JButton b)
	{
		b.setBackground(FIELD);
		b.setForeground(TEXT);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.setOpaque(true);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(3, 8, 3, 8)));
	}

	static void styleField(JTextComponent f)
	{
		f.setBackground(FIELD);
		f.setForeground(TEXT);
		f.setCaretColor(GREEN_BRIGHT);
		f.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER),
			BorderFactory.createEmptyBorder(2, 4, 2, 4)));
	}

	static void styleCombo(JComboBox<?> c)
	{
		c.setBackground(FIELD);
		c.setForeground(TEXT);
		c.setFont(FontManager.getRunescapeSmallFont());
	}
}
