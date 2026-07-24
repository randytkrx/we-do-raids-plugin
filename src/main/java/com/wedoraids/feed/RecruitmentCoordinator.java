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
package com.wedoraids.feed;

import com.wedoraids.WeDoRaidsConfig;
import com.wedoraids.panel.WeDoRaidsPanel;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class RecruitmentCoordinator
{
	@FunctionalInterface
	public interface PanelUpdater
	{
		void update(long generation, Consumer<WeDoRaidsPanel> update);
	}

	private final WeDoRaidsConfig config;
	private final List<RecruitEntry> demoEntries;
	private final Set<String> notifiedKeys;
	private final Set<String> activeTobHosts;
	private final Set<String> activeToaHosts;
	private final BooleanSupplier localBanned;
	private final BooleanSupplier localVerified;
	private final LongSupplier identityGeneration;
	private final Consumer<RecruitEntry> notifyRecruit;
	private final PanelUpdater panelUpdater;

	public RecruitmentCoordinator(WeDoRaidsConfig config, List<RecruitEntry> demoEntries, Set<String> notifiedKeys,
		Set<String> activeTobHosts, Set<String> activeToaHosts, BooleanSupplier localBanned,
		BooleanSupplier localVerified, LongSupplier identityGeneration, Consumer<RecruitEntry> notifyRecruit,
		PanelUpdater panelUpdater)
	{
		this.config = config;
		this.demoEntries = demoEntries;
		this.notifiedKeys = notifiedKeys;
		this.activeTobHosts = activeTobHosts;
		this.activeToaHosts = activeToaHosts;
		this.localBanned = localBanned;
		this.localVerified = localVerified;
		this.identityGeneration = identityGeneration;
		this.notifyRecruit = notifyRecruit;
		this.panelUpdater = panelUpdater;
	}

	public void accept(List<RecruitEntry> entries)
	{
		final long generation = identityGeneration.getAsLong();
		final FeedProjection projection = FeedProjection.project(entries, demoEntries, this::passesFilters,
			config.demoData());
		final Set<String> current = new HashSet<>();
		for (RecruitEntry entry : projection.getNotifiableLiveEntries())
		{
			final String key = entry.getSender() + '|' + entry.getRaidType() + '|'
				+ entry.getTimestamp().toEpochMilli() + '|' + entry.getMessage();
			if (current.add(key) && !notifiedKeys.contains(key)
				&& Duration.between(entry.getTimestamp(), Instant.now()).toMinutes() < 2)
			{
				if (identityGeneration.getAsLong() == generation)
				{
					notifyRecruit.accept(entry);
				}
			}
		}
		if (identityGeneration.getAsLong() != generation)
		{
			return;
		}
		notifiedKeys.clear();
		notifiedKeys.addAll(current);
		activeTobHosts.clear();
		activeTobHosts.addAll(projection.getTobHosts());
		activeToaHosts.clear();
		activeToaHosts.addAll(projection.getToaHosts());
		panelUpdater.update(generation, panel -> panel.setEntries(projection.getEntries()));
	}

	private boolean passesFilters(RecruitEntry entry)
	{
		return !localBanned.getAsBoolean()
			&& localVerified.getAsBoolean()
			&& raidEnabled(entry.getRaidType())
			&& matchesKeywordFilter(entry.getMessage())
			&& kindEnabled(entry.getKind())
			&& matchesTierFilter(entry.getTier());
	}

	private boolean matchesTierFilter(String tier)
	{
		final String filter = config.tierFilter().trim();
		if (filter.isEmpty() || tier == null)
		{
			return true;
		}
		for (String allowed : filter.split(","))
		{
			if (allowed.trim().equalsIgnoreCase(tier))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean kindEnabled(String kind)
	{
		return "RAID".equals(kind) || "LFG".equals(kind);
	}

	private boolean raidEnabled(RaidType raidType)
	{
		switch (raidType)
		{
			case TOB:
				return config.showTob();
			case COX:
				return config.showCox();
			case TOA:
				return config.showToa();
			default:
				return true;
		}
	}

	private boolean matchesKeywordFilter(String message)
	{
		final String filter = config.keywordFilter().trim();
		if (filter.isEmpty())
		{
			return true;
		}
		final String lower = message.toLowerCase();
		for (String keyword : filter.split(","))
		{
			keyword = keyword.trim().toLowerCase();
			if (!keyword.isEmpty() && lower.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}
}
