package com.wedoraids;

import static com.wedoraids.LifecycleTestSupport.feedConfig;
import static com.wedoraids.LifecycleTestSupport.invoke;
import static com.wedoraids.LifecycleTestSupport.setField;
import static org.junit.Assert.assertEquals;

import com.wedoraids.LifecycleTestSupport.RecordingNotificationPlugin;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

public class FeedAcceptanceTest
{
	@Test
	public void duplicateRawEntriesOnlyAttemptOneNotificationPerPayload()
		throws Exception
	{
		RecordingNotificationPlugin plugin = new RecordingNotificationPlugin();
		setField(plugin, "config", feedConfig());
		setField(plugin, "localVerified", true);
		RecruitEntry entry = new RecruitEntry("Alice", "WDR", RaidType.TOB, null, null, null, null, null,
			301, null, null, 0, "RAID", "fresh raid", Instant.now());

		invoke(plugin, "acceptEntries", Arrays.asList(entry, entry));

		assertEquals(1, plugin.notificationCount.get());
	}
}
