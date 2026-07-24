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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class FeedProjectionTest
{
	@Test
	public void project_filtersBothSourcesAndSortsNewestFirst_whenDemoIsEnabled()
	{
		final RecruitEntry rawOldest = entry("Raw Oldest", RaidType.TOB, Instant.ofEpochSecond(1));
		final RecruitEntry rawFiltered = entry("Raw Rejected", RaidType.COX, Instant.ofEpochSecond(3));
		final RecruitEntry demoMiddle = entry("Demo Middle", RaidType.TOA, Instant.ofEpochSecond(5));
		final RecruitEntry demoFiltered = entry("Demo Rejected", RaidType.COX, Instant.ofEpochSecond(7));
		final RecruitEntry rawNewest = entry("Raw Newest", RaidType.COX, Instant.ofEpochSecond(9));

		final FeedProjection projection = FeedProjection.project(
			Arrays.asList(rawOldest, rawFiltered, rawNewest), Arrays.asList(demoMiddle, demoFiltered),
			entry -> entry != rawFiltered && entry != demoFiltered, true);

		assertEquals(Arrays.asList(rawNewest, demoMiddle, rawOldest), projection.getEntries());
	}

	@Test
	public void project_excludesDemoEntries_whenDemoIsDisabled()
	{
		final RecruitEntry raw = entry("ToB Host", RaidType.TOB, Instant.ofEpochSecond(1));
		final RecruitEntry demo = entry("ToA Host", RaidType.TOA, Instant.ofEpochSecond(2));

		final FeedProjection projection = FeedProjection.project(Collections.singletonList(raw),
			Collections.singletonList(demo), entry -> true, false);

		assertEquals(Collections.singletonList(raw), projection.getEntries());
		assertEquals(Collections.singleton("tob host"), projection.getTobHosts());
		assertTrue(projection.getToaHosts().isEmpty());
	}

	@Test
	public void project_skipsNullAndEmptySenders_whenAggregatingHosts()
	{
		final RecruitEntry nullSender = entry(null, RaidType.TOB, Instant.ofEpochSecond(1));
		final RecruitEntry emptySender = entry(" <col=ff0000> </col> ", RaidType.TOA, Instant.ofEpochSecond(2));
		final RecruitEntry cox = entry("CoX Host", RaidType.COX, Instant.ofEpochSecond(3));

		final FeedProjection projection = FeedProjection.project(Arrays.asList(nullSender, emptySender, cox),
			Collections.emptyList(), entry -> true, false);

		assertTrue(projection.getTobHosts().isEmpty());
		assertTrue(projection.getToaHosts().isEmpty());
	}

	@Test
	public void project_normalizesAndPartitionsToBRaidAndToARaidHosts()
	{
		final RecruitEntry tob = entry(" <col=ff00aa> Theatre Host </col> ", RaidType.TOB,
			Instant.ofEpochSecond(1));
		final RecruitEntry toa = entry(" Tombs Host ", RaidType.TOA, Instant.ofEpochSecond(2));
		final RecruitEntry cox = entry("Chambers Host", RaidType.COX, Instant.ofEpochSecond(3));

		final FeedProjection projection = FeedProjection.project(Arrays.asList(tob, toa, cox),
			Collections.emptyList(), entry -> true, false);

		assertEquals(Collections.singleton("theatre host"), projection.getTobHosts());
		assertEquals(Collections.singleton("tombs host"), projection.getToaHosts());
		assertFalse(projection.getTobHosts().contains("chambers host"));
		assertFalse(projection.getToaHosts().contains("chambers host"));
	}

	@Test
	public void project_defensivelyCopiesInputLists()
	{
		final RecruitEntry raw = entry("Raw", RaidType.TOB, Instant.ofEpochSecond(1));
		final RecruitEntry demo = entry("Demo", RaidType.TOA, Instant.ofEpochSecond(2));
		final List<RecruitEntry> rawEntries = new ArrayList<>(Collections.singletonList(raw));
		final List<RecruitEntry> demoEntries = new ArrayList<>(Collections.singletonList(demo));

		final FeedProjection projection = FeedProjection.project(rawEntries, demoEntries, entry -> true, true);
		rawEntries.clear();
		demoEntries.clear();

		assertEquals(Arrays.asList(demo, raw), projection.getEntries());
		assertEquals(Collections.singleton("raw"), projection.getTobHosts());
		assertEquals(Collections.singleton("demo"), projection.getToaHosts());
	}

	@Test
	public void project_returnsImmutableEntriesAndHostSets()
	{
		final FeedProjection projection = FeedProjection.project(Collections.singletonList(
			entry("ToB Host", RaidType.TOB, Instant.ofEpochSecond(1))), Collections.emptyList(), entry -> true, false);

		assertUnmodifiable(projection.getEntries());
		assertUnmodifiable(projection.getNotifiableLiveEntries());
		assertUnmodifiable(projection.getTobHosts());
		assertUnmodifiable(projection.getToaHosts());
	}

	@Test
	public void project_filtersEachSourceEntryOnceAndKeepsRawMatchesSeparateFromDemo()
	{
		final RecruitEntry rawIncluded = entry("Raw Included", RaidType.TOB, Instant.ofEpochSecond(1));
		final RecruitEntry rawRejected = entry("Raw Rejected", RaidType.COX, Instant.ofEpochSecond(2));
		final RecruitEntry demoIncluded = entry("Demo Included", RaidType.TOA, Instant.ofEpochSecond(3));
		final AtomicInteger predicateCalls = new AtomicInteger();

		final FeedProjection projection = FeedProjection.project(Arrays.asList(rawIncluded, rawRejected),
			Collections.singletonList(demoIncluded), entry ->
			{
				predicateCalls.incrementAndGet();
				return entry != rawRejected;
			}, true);

		assertEquals(3, predicateCalls.get());
		assertEquals(Collections.singletonList(rawIncluded), projection.getNotifiableLiveEntries());
		assertEquals(Arrays.asList(demoIncluded, rawIncluded), projection.getEntries());
		assertUnmodifiable(projection.getNotifiableLiveEntries());
	}

	private static void assertUnmodifiable(List<?> values)
	{
		try
		{
			values.clear();
			throw new AssertionError("Expected an unmodifiable list");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	private static void assertUnmodifiable(Set<?> values)
	{
		try
		{
			values.clear();
			throw new AssertionError("Expected an unmodifiable set");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	private static RecruitEntry entry(String sender, RaidType raidType, Instant timestamp)
	{
		return new RecruitEntry(sender, "WDR", raidType, null, null, null, null, null, 0, null, null, 0,
			"RAID", "message", timestamp);
	}
}
