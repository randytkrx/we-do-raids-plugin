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
package com.wedoraids.board;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

public class RaidBoardOverlayTest
{
	@Test
	public void matches_requiresHostsToBeBoundedBySpaces()
		throws Exception
	{
		assertTrue(matches("alice", "alice"));
		assertTrue(matches("raid alice +2", "alice"));
		assertFalse(matches("malice", "alice"));
		assertFalse(matches("alice,bob", "alice"));
		assertFalse(matches("alice", ""));
	}

	@Test
	public void render_usesAllValidCellsWhoseSpanContainsTheNameMidpoint()
	{
		WidgetNode board = WidgetNode.board();
		board.children(
			WidgetNode.text("1/8", new Rectangle(20, 20, 20, 12)),
			WidgetNode.text("Alice", new Rectangle(50, 20, 40, 12)),
			WidgetNode.text("416", new Rectangle(100, 16, 30, 20)),
			WidgetNode.text("top boundary", new Rectangle(140, 26, 20, 0)),
			WidgetNode.text("bottom boundary", new Rectangle(170, 14, 20, 12)),
			WidgetNode.text("other row", new Rectangle(5, 60, 150, 12)),
			WidgetNode.text("zero width", new Rectangle(5, 20, 0, 12)),
			WidgetNode.text("no bounds", null));

		BufferedImage image = render(board, Collections.singleton("alice"));

		assertTrue(alphaAt(image, 13, 19) > 0);
		assertTrue(alphaAt(image, 186, 19) > 0);
		assertEquals(0, alphaAt(image, 12, 19));
		assertEquals(0, alphaAt(image, 187, 19));
		assertEquals(0, alphaAt(image, 50, 60));
	}

	@Test
	public void render_doesNotHighlightRowsOutsideTheScrolledViewport()
	{
		WidgetNode board = WidgetNode.board();
		board.height = 30;
		board.scrollHeight = 100;
		board.scrollY = 20;
		WidgetNode name = WidgetNode.text("Alice", new Rectangle(50, 20, 40, 12));
		name.relativeY = 60;
		board.children(name);

		BufferedImage image = render(board, Collections.singleton("alice"));

		assertEquals(0, alphaAt(image, 50, 20));
	}

	@Test
	public void render_readsEachTextBoundsOnceForTwentyMatchesOnEightyWidgets()
	{
		WidgetNode board = WidgetNode.board();
		WidgetNode[] cells = new WidgetNode[80];
		for (int index = 0; index < cells.length; index++)
		{
			int row = index / 4;
			String text = index % 4 == 1 ? "Host " + row : "cell " + index;
			cells[index] = WidgetNode.text(text, new Rectangle(20 + index % 4 * 50, 10 + row * 14, 40, 12));
		}
		board.children(cells);
		Set<String> hosts = new HashSet<>();
		for (int row = 0; row < 20; row++)
		{
			hosts.add("host " + row);
		}

		render(board, hosts);

		int boundsCalls = 0;
		for (WidgetNode cell : cells)
		{
			boundsCalls += cell.boundsCalls.get();
		}
		assertEquals("expected one bounds lookup per text widget", cells.length, boundsCalls);
	}

	private static boolean matches(String rowText, String host)
		throws Exception
	{
		Method method = RaidBoardOverlay.class.getDeclaredMethod("matches", String.class, Set.class);
		method.setAccessible(true);
		return (Boolean) method.invoke(null, rowText, Collections.singleton(host));
	}

	private static BufferedImage render(WidgetNode board, Set<String> hosts)
	{
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> method.getName().equals("getWidget") && args.length == 1
				&& args[0].equals(InterfaceID.TobPartylist.UNIVERSE) ? board.widget : defaultValue(method.getReturnType()));
		RaidBoardOverlay overlay = new RaidBoardOverlay(client, () -> hosts, Collections::emptySet);
		BufferedImage image = new BufferedImage(240, 360, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			overlay.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private static int alphaAt(BufferedImage image, int x, int y)
	{
		return image.getRGB(x, y) >>> 24;
	}

	private static Object defaultValue(Class<?> type)
	{
		if (!type.isPrimitive())
		{
			return null;
		}
		if (type == boolean.class)
		{
			return false;
		}
		if (type == char.class)
		{
			return '\0';
		}
		return 0;
	}

	private static final class WidgetNode implements InvocationHandler
	{
		private final Widget widget = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
			new Class<?>[]{Widget.class}, this);
		private final String text;
		private final Rectangle bounds;
		private final AtomicInteger boundsCalls = new AtomicInteger();
		private Widget parent;
		private Widget[] staticChildren;
		private int height;
		private int scrollHeight;
		private int scrollY;
		private int relativeY;

		private WidgetNode(String text, Rectangle bounds)
		{
			this.text = text;
			this.bounds = bounds;
			this.height = bounds == null ? 0 : bounds.height;
		}

		private static WidgetNode board()
		{
			return new WidgetNode("", null);
		}

		private static WidgetNode text(String text, Rectangle bounds)
		{
			return new WidgetNode(text, bounds);
		}

		private void children(WidgetNode... children)
		{
			staticChildren = new Widget[children.length];
			for (int index = 0; index < children.length; index++)
			{
				children[index].parent = widget;
				staticChildren[index] = children[index].widget;
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
		{
			switch (method.getName())
			{
				case "getText":
					return text;
				case "getBounds":
					boundsCalls.incrementAndGet();
					return bounds;
				case "getParent":
					return parent;
				case "getStaticChildren":
					return staticChildren;
				case "getHeight":
					return height;
				case "getScrollHeight":
					return scrollHeight;
				case "getScrollY":
					return scrollY;
				case "getRelativeY":
					return relativeY;
				case "isHidden":
					return false;
				default:
					return defaultValue(method.getReturnType());
			}
		}
	}
}
