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
package com.wedoraids.host;

import com.wedoraids.ui.WdrButton;
import com.wedoraids.ui.WdrTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;

final class HostInactivityGuard
{
	private static final int IDLE_MS = 7 * 60 * 1000;
	private static final int PROMPT_SECONDS = 60;

	private final HostLiveState liveState;
	private final BooleanSupplier hasLivePost;
	private final Runnable rebuildLiveControls;
	private final Runnable closeLivePost;
	private final Runnable onIdleWarn;
	private final Timer idleTimer;
	private final Timer promptTimer;
	private final JPanel banner = new JPanel();
	private final JLabel countdown = new JLabel();
	private int promptRemaining;
	private boolean prompting;

	HostInactivityGuard(HostLiveState liveState, BooleanSupplier hasLivePost,
		Runnable rebuildLiveControls, Runnable closeLivePost, Runnable onIdleWarn)
	{
		this.liveState = liveState;
		this.hasLivePost = hasLivePost;
		this.rebuildLiveControls = rebuildLiveControls;
		this.closeLivePost = closeLivePost;
		this.onIdleWarn = onIdleWarn;
		idleTimer = new Timer(IDLE_MS, e -> showPrompt());
		idleTimer.setRepeats(false);
		promptTimer = new Timer(1000, e -> tickPrompt());
		buildBanner();
	}

	JPanel banner()
	{
		return banner;
	}

	boolean isPrompting()
	{
		return prompting;
	}

	void enterLivePost()
	{
		prompting = false;
		reset();
	}

	void exitLivePost()
	{
		prompting = false;
		idleTimer.stop();
		promptTimer.stop();
	}

	void pause()
	{
		idleTimer.stop();
		promptTimer.stop();
		prompting = false;
	}

	void stop()
	{
		idleTimer.stop();
		promptTimer.stop();
	}

	void reset()
	{
		if (liveState.isStopped())
		{
			return;
		}
		promptTimer.stop();
		final boolean wasPrompting = prompting;
		prompting = false;
		if (hasLivePost.getAsBoolean())
		{
			idleTimer.restart();
		}
		if (wasPrompting)
		{
			rebuildLiveControls.run();
		}
	}

	private void buildBanner()
	{
		banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
		banner.setBackground(new Color(46, 34, 18));
		banner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(214, 170, 80)),
			BorderFactory.createEmptyBorder(8, 10, 10, 10)));
		banner.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel question = new JLabel("Still hosting this raid?");
		question.setFont(FontManager.getRunescapeSmallFont());
		question.setForeground(new Color(240, 210, 150));
		fullWidth(question);
		banner.add(question);
		banner.add(Box.createVerticalStrut(2));

		countdown.setFont(FontManager.getRunescapeSmallFont());
		countdown.setForeground(WdrTheme.TEXT_DIM);
		fullWidth(countdown);
		banner.add(countdown);
		banner.add(Box.createVerticalStrut(7));

		WdrButton here = new WdrButton("I'm here, keep it open", WdrButton.Variant.PRIMARY);
		here.addActionListener(e -> reset());
		fullWidth(here);
		banner.add(here);
		banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, banner.getPreferredSize().height));
	}

	private void showPrompt()
	{
		if (liveState.isStopped() || !hasLivePost.getAsBoolean())
		{
			return;
		}
		prompting = true;
		promptRemaining = PROMPT_SECONDS;
		countdown.setText("Auto-closing in " + promptRemaining + "s…");
		promptTimer.restart();
		rebuildLiveControls.run();
		if (onIdleWarn != null)
		{
			onIdleWarn.run();
		}
	}

	private void tickPrompt()
	{
		if (liveState.isStopped())
		{
			return;
		}
		promptRemaining--;
		if (promptRemaining <= 0)
		{
			promptTimer.stop();
			prompting = false;
			closeLivePost.run();
			if (!liveState.isStopped())
			{
				idleTimer.restart();
			}
			return;
		}
		countdown.setText("Auto-closing in " + promptRemaining + "s…");
	}

	private static void fullWidth(javax.swing.JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}
}
