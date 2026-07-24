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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

final class DemoRecruitEntries
{
	private DemoRecruitEntries()
	{
	}

	static List<RecruitEntry> create(Instant now)
	{
		return Arrays.asList(
			entry("Olm Enjoyer", "WDR ToB", RaidType.TOB, "Standard", null, "+2", "mdps/rdps", "trio", 447, null, "olm", "RAID", "trio w447 mdps/rdps +2 phub olm", now, 13, 84),
			entry("Pogtank Pete", "WDR ToB", RaidType.TOB, "HM Exp", "100+ kc", "+1", "mdps", "5 man", 364, null, "hmt6", "RAID", "+1 mdps pogstack 100+kc phub hmt6 w364", now, 12, 412),
			entry("Frozen Faz", "WDR ToB", RaidType.TOB, "Advanced", null, "+1", "frz", "trio", 489, null, "screen", "RAID", "+1 frz trio w489 scy pref ph screen", now, 11, 268),
			entry("Rngesus Rex", "WDR ToB", RaidType.TOB, "Standard", null, "+1", "range", null, 507, null, null, "RAID", "507+1 range", now, 10, 61),
			entry("Sanguine Sal", "WDR ToB", RaidType.TOB, "HM", "HM", "+2", "rdps/nfrz", null, 403, null, null, "RAID", "looking for 1 rdps 2 freeze w403", now, 9, 337),
			entry("Prep School", "WDR CoX", RaidType.COX, "Scaled", "3+4", "+2", null, null, 0, null, null, "RAID", "c 3+4", now, 8, 143),
			entry("Skip Steady", "WDR CoX", RaidType.COX, "FFA", "3+4", "+2", "melee", null, 0, null, null, "RAID", "+2 3+4 mele taken", now, 7, 512),
			entry("Cm Chad", "WDR CoX", RaidType.COX, "FFA CM", "CM", "+1", null, null, 0, null, "Cm Chad", "RAID", "+1 fc: Cm Chad ffa C.M", now, 6, 806),
			entry("Fresh Learner", "WDR CoX", RaidType.COX, "Learner", null, null, null, null, 0, null, null, "LFG", "LFG, new to raids never done any beside for the diary", now, 5, 3),
			entry("Invo Andy", "WDR ToA", RaidType.TOA, "300-445", "400 invo split", null, null, null, 503, null, null, "LFG", "400 w503 split all", now, 4, 221),
			entry("Kit Chaser", "WDR ToA", RaidType.TOA, "0-295", "200 invo", "+1", null, null, 362, null, null, "RAID", "200 +1 w362", now, 3, 47),
			entry("Warden Wes", "WDR ToA", RaidType.TOA, "450+", "450-500 invo", "+1", null, "duo", 520, null, null, "RAID", "+1 duos 450-500s w520", now, 2, 690),
			entry("Mog Hunter", "WDR ToA", RaidType.TOA, "450+", "450 invo kit", null, null, "trio", 0, null, null, "LFG", "any 450 duo/trio zebak transmog?", now, 1, 455),
			entry("Nylo Nick", "WDR ToB", RaidType.TOB, "HM Exp", null, "+1", "nfrz", "5 man", 516, null, "nylo", "RAID", "+1 - nfrz - w516 (pogstack) - ph: nylo", now, 0, 158));
	}

	private static RecruitEntry entry(String sender, String source, RaidType raid, String tier,
		String mode, String spots, String roles, String party, int world, String region, String host,
		String kind, String message, Instant now, int minutesAgo, int kc)
	{
		return new RecruitEntry(sender, source, raid, tier, mode, spots, roles, party, world, region, host,
			kc, kind, message, now.minus(Duration.ofMinutes(minutesAgo)));
	}
}
