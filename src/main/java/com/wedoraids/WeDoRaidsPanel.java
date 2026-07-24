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
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

class WeDoRaidsPanel extends PluginPanel
{
	private final HostFormPanel hostForm;
	private final RecruitPanelHeader header;
	private final RecruitFilterBar filterBar;
	private final RecruitListPanel recruitList;
	private boolean verified;

	WeDoRaidsPanel(WeDoRaidsConfig config, HostDependencies hostDependencies,
		PanelDependencies panelDependencies)
	{
		super(false);
		header = new RecruitPanelHeader(config, panelDependencies.onRefresh());
		filterBar = new RecruitFilterBar(config, panelDependencies.saveFilter(), this::rebuildRecruitList);
		recruitList = new RecruitListPanel(filterBar, panelDependencies.onHopWorld(),
			panelDependencies.onJoinHub(), header::setEntryCount, header.logo());

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(WdrTheme.BACKGROUND);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(header);
		top.add(Box.createVerticalStrut(6));

		JPanel demoBanner = header.demoBanner();
		demoBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(demoBanner);
		header.refreshDemoBanner();

		hostForm = new HostFormPanel(hostDependencies);
		hostForm.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(hostForm);
		top.add(Box.createVerticalStrut(6));

		top.add(divider());
		top.add(Box.createVerticalStrut(6));
		top.add(filterBar);
		filterBar.restoreSelection();
		top.add(Box.createVerticalStrut(4));
		top.add(recruitList.countLabel());
		top.add(Box.createVerticalStrut(6));

		add(top, BorderLayout.NORTH);
		add(recruitList.scrollPane(), BorderLayout.CENTER);
		add(header.statusBar(), BorderLayout.SOUTH);
		recruitList.rebuild();
	}

	void setBridgeStatus(BridgeStatus status)
	{
		header.setBridgeStatus(status);
	}

	void setBanned(boolean banned)
	{
		recruitList.setBanned(banned);
	}

	void setVerified(boolean verified)
	{
		if (this.verified == verified)
		{
			return;
		}
		this.verified = verified;
		recruitList.setVerified(verified);
	}

	void refreshHostTiers()
	{
		hostForm.refreshTiersPublic();
	}

	void refreshCoxLayout()
	{
		hostForm.refreshCoxLayout();
	}

	void enterHostLive(String messageId)
	{
		hostForm.enterLivePost(messageId);
	}

	void exitHostLive()
	{
		hostForm.exitLivePost();
	}

	void shutdown()
	{
		hostForm.exitLivePost();
		hostForm.stopTimers();
	}

	void setLoggedIn(boolean loggedIn)
	{
		recruitList.setLoggedIn(loggedIn);
	}

	void setEntries(List<RecruitEntry> newEntries)
	{
		header.refreshDemoBanner();
		recruitList.setEntries(newEntries);
	}

	void clear()
	{
		recruitList.clear();
	}

	private void rebuildRecruitList()
	{
		recruitList.rebuild();
	}

	private static JPanel divider()
	{
		JPanel line = new JPanel();
		line.setBackground(WdrTheme.BORDER);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}
}
