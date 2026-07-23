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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;

@ConfigGroup("wedoraids")
public interface WeDoRaidsConfig extends Config
{
	@ConfigSection(
		name = "Filters",
		description = "Which recruiting calls to show",
		position = 0
	)
	String filtersSection = "filters";

	@ConfigSection(
		name = "Verification",
		description = "No bridge traffic occurs until you enter a verification key. Demo data stays fully local. "
			+ "With a key, feed requests send your RuneScape name and key for verification and ban-list enforcement; "
			+ "posting, updating, or closing also sends raid, tier, world, size, spots, roles, scale, fc, layout, partyHub, desc, "
			+ "and messageId when applicable, plus your in-game name.",
		position = 1
	)
	String remoteSection = "remote";

	// Hidden: remembers the panel's filter-bar selection between sessions.
	@ConfigItem(
		keyName = "lastRaidFilter",
		name = "",
		description = "",
		hidden = true
	)
	default String lastRaidFilter()
	{
		return "All raids";
	}

	@ConfigItem(
		keyName = "lastTierFilter",
		name = "",
		description = "",
		hidden = true
	)
	default String lastTierFilter()
	{
		return "All tiers";
	}

	@ConfigItem(
		keyName = "showTob",
		name = "Theatre of Blood",
		description = "Show ToB recruiting calls",
		section = filtersSection,
		position = 0
	)
	default boolean showTob()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCox",
		name = "Chambers of Xeric",
		description = "Show CoX recruiting messages",
		section = filtersSection,
		position = 1
	)
	default boolean showCox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showToa",
		name = "Tombs of Amascut",
		description = "Show ToA recruiting calls",
		section = filtersSection,
		position = 2
	)
	default boolean showToa()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tierFilter",
		name = "Only these tiers",
		description = "Comma-separated WDR tiers to show (e.g. 'Learner, Standard, Advanced') — hide the ones you don't have the KC for. Leave empty to show all tiers",
		section = filtersSection,
		position = 6
	)
	default String tierFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "keywordFilter",
		name = "Keyword filter",
		description = "Only show messages containing one of these comma-separated words (e.g. 'hmt, 300'). Leave empty for all",
		section = filtersSection,
		position = 4
	)
	default String keywordFilter()
	{
		return "";
	}

	@ConfigItem(
		keyName = "notifyOnRecruit",
		name = "Notify on recruit",
		description = "Send a notification when a matching recruiting message is seen",
		position = 3
	)
	default Notification notifyOnRecruit()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "autoPartyHub",
		name = "Auto party hub",
		description = "When hosting, auto-generate a short party passphrase (like 'catdog') and create the RuneLite party for it when your raid is posted, so joiners can use the Party plugin's ph command.",
		position = 5
	)
	default boolean autoPartyHub()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hostIdleNotify",
		name = "Host inactivity alert",
		description = "Send a desktop notification when your hosted raid has gone idle and is about to auto-close, so you can keep it open even with the panel closed",
		position = 4
	)
	default Notification hostIdleNotify()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "remoteFeedKey",
		name = "Verification key",
		description = "Your personal key from the We Do Raids Discord — type the verify command there to get it. "
			+ "The key is stored as a secret RuneLite configuration value. Adding it enables feed requests that send your "
			+ "RuneScape name and key to the WDR server so you can see and host raids.",
		section = remoteSection,
		position = 0,
		secret = true
	)
	default String remoteFeedKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "remoteFeedUrl",
		name = "Bridge URL (advanced)",
		description = "Leave blank to use the default We Do Raids bridge. Only set this to point at your own bridge for testing.",
		section = remoteSection,
		position = 1
	)
	default String remoteFeedUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "demoData",
		name = "Demo data",
		description = "Show local sample recruiting calls instead of the live feed to preview the panel",
		section = remoteSection,
		position = 3
	)
	default boolean demoData()
	{
		return false;
	}
}
