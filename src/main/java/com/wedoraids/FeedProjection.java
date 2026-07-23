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
			final String normalizedSender = RaidBoardOverlay.normalize(sender);
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
