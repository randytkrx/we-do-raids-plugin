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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class FeedProjection
{
	private final List<RecruitEntry> entries;
	private final List<RecruitEntry> notifiableLiveEntries;
	private final Set<String> tobHosts;
	private final Set<String> toaHosts;

	private FeedProjection(List<RecruitEntry> entries, List<RecruitEntry> notifiableLiveEntries,
		Set<String> tobHosts, Set<String> toaHosts)
	{
		this.entries = List.copyOf(entries);
		this.notifiableLiveEntries = List.copyOf(notifiableLiveEntries);
		this.tobHosts = Set.copyOf(tobHosts);
		this.toaHosts = Set.copyOf(toaHosts);
	}

	static FeedProjection project(List<RecruitEntry> rawEntries, List<RecruitEntry> demoEntries,
		Predicate<RecruitEntry> includePredicate, boolean demoEnabled)
	{
		final List<RecruitEntry> notifiableLiveEntries = new ArrayList<>();
		addMatching(notifiableLiveEntries, rawEntries, includePredicate);
		final List<RecruitEntry> visibleEntries = new ArrayList<>(notifiableLiveEntries);
		if (demoEnabled)
		{
			addMatching(visibleEntries, demoEntries, includePredicate);
		}
		visibleEntries.sort(Comparator.comparing(RecruitEntry::getTimestamp).reversed());

		final Set<String> tobHosts = new HashSet<>();
		final Set<String> toaHosts = new HashSet<>();
		for (RecruitEntry entry : visibleEntries)
		{
			final String sender = entry.getSender();
			if (sender == null)
			{
				continue;
			}
			final String normalizedSender = PlayerNameNormalizer.normalize(sender);
			if (normalizedSender.isEmpty())
			{
				continue;
			}
			if (entry.getRaidType() == RaidType.TOB)
			{
				tobHosts.add(normalizedSender);
			}
			else if (entry.getRaidType() == RaidType.TOA)
			{
				toaHosts.add(normalizedSender);
			}
		}
		return new FeedProjection(visibleEntries, notifiableLiveEntries, tobHosts, toaHosts);
	}

	private static void addMatching(List<RecruitEntry> visibleEntries, List<RecruitEntry> entries,
		Predicate<RecruitEntry> filter)
	{
		for (RecruitEntry entry : entries)
		{
			if (filter.test(entry))
			{
				visibleEntries.add(entry);
			}
		}
	}

	List<RecruitEntry> getEntries()
	{
		return entries;
	}

	List<RecruitEntry> getNotifiableLiveEntries()
	{
		return notifiableLiveEntries;
	}

	Set<String> getTobHosts()
	{
		return tobHosts;
	}

	Set<String> getToaHosts()
	{
		return toaHosts;
	}
}
