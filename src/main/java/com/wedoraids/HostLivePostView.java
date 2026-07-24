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
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

final class HostLivePostView extends JPanel
{
	private final HostInactivityGuard inactivityGuard;
	private final Consumer<String> fillRole;
	private final Runnable decrementSpot;
	private final Runnable beginEdit;
	private final Runnable close;
	private final JLabel status = new JLabel(" ");

	HostLivePostView(HostInactivityGuard inactivityGuard, Consumer<String> fillRole,
		Runnable decrementSpot, Runnable beginEdit, Runnable close)
	{
		this.inactivityGuard = inactivityGuard;
		this.fillRole = fillRole;
		this.decrementSpot = decrementSpot;
		this.beginEdit = beginEdit;
		this.close = close;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(WdrTheme.TEXT_DIM);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	JLabel statusLabel()
	{
		return status;
	}

	void rebuild(Map<String, String> fields)
	{
		removeAll();
		if (inactivityGuard.isPrompting())
		{
			add(inactivityGuard.banner());
			add(Box.createVerticalStrut(6));
		}
		JPanel card = card();
		final String spots = fields.get("spots");
		JLabel title = new JLabel("Your " + raidLabel(fields.get("raid")) + " raid is live");
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(WdrTheme.TEXT);
		fullWidth(title);
		card.add(title);
		card.add(Box.createVerticalStrut(4));
		card.add(row(spotsChip(spots)));
		card.add(Box.createVerticalStrut(9));

		final List<String> roles = roles(fields);
		if (roles.isEmpty())
		{
			addSpotControl(card);
		}
		else
		{
			addRoleControls(card, roles);
		}
		card.add(Box.createVerticalStrut(10));
		addActionButtons(card);
		card.add(Box.createVerticalStrut(6));
		card.add(status);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		add(card);
		revalidate();
		repaint();
	}

	void setStatus(String message, boolean error)
	{
		status.setText("<html><body style='width:180px'>" + escapeHtml(message) + "</body></html>");
		status.setForeground(error ? WdrTheme.ERROR : WdrTheme.TEXT_DIM);
	}

	private static JPanel card()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(WdrTheme.CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 0, 0, 0, WdrTheme.GREEN),
			BorderFactory.createEmptyBorder(9, 10, 10, 10)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	private static JLabel spotsChip(String spots)
	{
		JLabel chip = new JLabel((spots == null || spots.isEmpty() ? "-" : spots) + " open");
		chip.setFont(FontManager.getRunescapeSmallFont());
		chip.setForeground(WdrTheme.TEXT);
		chip.setOpaque(true);
		chip.setBackground(WdrTheme.FIELD);
		chip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.BORDER),
			BorderFactory.createEmptyBorder(1, 8, 1, 8)));
		return chip;
	}

	private void addRoleControls(JPanel card, List<String> roles)
	{
		card.add(hint("Tap a role when it's filled:"));
		card.add(Box.createVerticalStrut(5));
		JPanel grid = new JPanel(new GridLayout(0, Math.min(2, roles.size()), 5, 5));
		grid.setOpaque(false);
		for (String role : roles)
		{
			WdrButton button = new WdrButton(role, WdrButton.Variant.PRIMARY);
			button.addActionListener(e -> fillRole.accept(role));
			grid.add(button);
		}
		fullWidth(grid);
		card.add(grid);
		card.add(Box.createVerticalStrut(6));
		WdrButton other = new WdrButton("Filled another spot", WdrButton.Variant.GHOST);
		other.addActionListener(e -> decrementSpot.run());
		fullWidth(other);
		card.add(other);
	}

	private void addSpotControl(JPanel card)
	{
		card.add(hint("Someone joined? Drop a spot:"));
		card.add(Box.createVerticalStrut(5));
		WdrButton minusSpot = new WdrButton("−1 open spot", WdrButton.Variant.PRIMARY);
		minusSpot.addActionListener(e -> decrementSpot.run());
		fullWidth(minusSpot);
		card.add(minusSpot);
	}

	private void addActionButtons(JPanel card)
	{
		WdrButton edit = new WdrButton("Edit details", WdrButton.Variant.GHOST);
		edit.addActionListener(e -> beginEdit.run());
		fullWidth(edit);
		card.add(edit);
		card.add(Box.createVerticalStrut(6));
		WdrButton closeButton = new WdrButton("Close raid", WdrButton.Variant.DANGER);
		closeButton.addActionListener(e -> close.run());
		fullWidth(closeButton);
		card.add(closeButton);
	}

	private static List<String> roles(Map<String, String> fields)
	{
		final List<String> roles = new ArrayList<>();
		final String raw = fields.get("roles");
		if (raw != null && !raw.isBlank())
		{
			for (String part : raw.split(","))
			{
				final String role = part.trim();
				if (!role.isEmpty())
				{
					roles.add(role);
				}
			}
		}
		return roles;
	}

	private static JLabel hint(String text)
	{
		JLabel hint = new JLabel(text);
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(WdrTheme.TEXT_DIM);
		fullWidth(hint);
		return hint;
	}

	private static JPanel row(Component item)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setOpaque(false);
		row.add(item);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static void fullWidth(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}

	private static String raidLabel(String code)
	{
		if (code == null)
		{
			return "";
		}
		switch (code.toUpperCase())
		{
			case "TOB": return "ToB";
			case "COX": return "CoX";
			case "TOA": return "ToA";
			default: return code;
		}
	}

	private static String escapeHtml(String value)
	{
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
