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
package net.runelite.client.plugins.wedoraids;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * Highlights parties on the ToB ("Performers for the Theatre") and ToA ("Tombs of
 * Amascut Party List") boards whose leader has an active WDR callout. Reads the board
 * interfaces generically, so it doesn't depend on the exact row layout.
 */
class RaidBoardOverlay extends Overlay
{
	private static final Color FILL = new Color(72, 187, 110, 70);
	private static final Color BORDER = new Color(128, 226, 150);

	private final Client client;
	private final Supplier<Set<String>> tobHosts;
	private final Supplier<Set<String>> toaHosts;
	private final BufferedImage icon;

	RaidBoardOverlay(Client client, Supplier<Set<String>> tobHosts, Supplier<Set<String>> toaHosts)
	{
		this.client = client;
		this.tobHosts = tobHosts;
		this.toaHosts = toaHosts;
		this.icon = loadIcon();
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	private static BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(RaidBoardOverlay.class, "wdr.png");
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		// The scrollable board of all parties (what we mainly want to highlight)...
		renderBoard(g, InterfaceID.TobPartylist.UNIVERSE, tobHosts.get());
		renderBoard(g, InterfaceID.ToaPartylist.UNIVERSE, toaHosts.get());
		// ...plus the single-party details view (shown when you open/are in a party).
		renderBoard(g, InterfaceID.TobPartydetails.UNIVERSE, tobHosts.get());
		renderBoard(g, InterfaceID.ToaPartydetails.UNIVERSE, toaHosts.get());
		return null;
	}

	private void renderBoard(Graphics2D g, int rootId, Set<String> hosts)
	{
		final Widget board = client.getWidget(rootId);
		if (board == null || board.isHidden())
		{
			return;
		}

		if (hosts == null || hosts.isEmpty())
		{
			return;
		}

		final List<Widget> texts = new ArrayList<>();
		collectText(board, texts);

		for (Widget w : texts)
		{
			final String norm = normalize(w.getText());
			if (norm.isEmpty() || !matches(norm, hosts))
			{
				continue;
			}
			final Rectangle b = w.getBounds();
			if (b == null || b.width <= 0)
			{
				continue;
			}
			// A row scrolled out of the list gets its bounds CLAMPED to the viewport edge
			// (so the highlight looks stuck at the bottom). Decide visibility from the true
			// scroll position instead of the clamped bounds.
			if (scrolledOut(w))
			{
				continue;
			}
			// Span the actual row content (leftmost cell to rightmost cell), so the box
			// never runs past the board edge, and keep the icon in the left margin so it
			// never covers the party-size ("1/8") column.
			final Rectangle row = rowExtent(texts, b);
			final int size = Math.min(16, Math.max(12, b.height));
			// Box pulled in ~10px on each side vs the row content; the icon stays parked
			// just left of the first column ("1/8"), independent of the box edge.
			final int left = row.x - (size - 5);
			final int right = row.x + row.width - 4;
			final int top = b.y - 1;
			final int height = b.height;
			final int width = right - left;

			g.setColor(FILL);
			g.fillRect(left, top, width, height);
			g.setColor(BORDER);
			g.drawRect(left, top, width, height);
			if (icon != null)
			{
				g.drawImage(icon, row.x - size - 3, b.y + (b.height - size) / 2, size, size, null);
			}
		}
	}

	/**
	 * True if {@code w} is a scroll-list row currently scrolled out of view. OSRS clamps a
	 * scrolled-out row's on-screen bounds to the viewport edge, so we can't trust getBounds();
	 * instead we take the row's true content position (unscrolled/unclamped, summed from
	 * relative offsets) minus the container's scroll offset, and compare to the viewport height.
	 */
	private static boolean scrolledOut(Widget w)
	{
		// Nearest scrollable ancestor (content taller than the visible window).
		Widget sc = null;
		for (Widget p = w; p != null; p = p.getParent())
		{
			if (p.getHeight() > 0 && p.getScrollHeight() > p.getHeight())
			{
				sc = p;
				break;
			}
		}
		if (sc == null)
		{
			return false; // not in a scroll list (e.g. the single-party details view)
		}
		int contentY = 0;
		for (Widget p = w; p != null && p != sc; p = p.getParent())
		{
			contentY += p.getRelativeY();
		}
		final int screenY = contentY - sc.getScrollY();
		return screenY < -2 || screenY + w.getHeight() > sc.getHeight() + 2;
	}

	/** Bounding box across all text cells sharing the matched name's row. */
	private static Rectangle rowExtent(List<Widget> texts, Rectangle name)
	{
		int minX = name.x;
		int maxX = name.x + name.width;
		final int mid = name.y + name.height / 2;
		for (Widget o : texts)
		{
			final Rectangle ob = o.getBounds();
			if (ob == null || ob.width <= 0)
			{
				continue;
			}
			// Same row if this cell's vertical span contains the name row's midline.
			if (mid >= ob.y && mid <= ob.y + ob.height)
			{
				minX = Math.min(minX, ob.x);
				maxX = Math.max(maxX, ob.x + ob.width);
			}
		}
		return new Rectangle(minX, name.y, maxX - minX, name.height);
	}

	private static void collectText(Widget w, List<Widget> out)
	{
		if (w == null || w.isHidden())
		{
			return;
		}
		if (w.getText() != null && !w.getText().isEmpty())
		{
			out.add(w);
		}
		for (Widget[] arr : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (arr != null)
			{
				for (Widget c : arr)
				{
					collectText(c, out);
				}
			}
		}
	}

	static String normalize(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replaceAll("<[^>]+>", "").replace(' ', ' ').toLowerCase().trim();
	}

	private static boolean matches(String rowText, Set<String> hosts)
	{
		for (String h : hosts)
		{
			if (!h.isEmpty() && (rowText.equals(h) || (" " + rowText + " ").contains(" " + h + " ")))
			{
				return true;
			}
		}
		return false;
	}
}
