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

import java.time.Instant;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * A normalized bridge/demo recruitment domain record.
 */
@Value
public class RecruitEntry
{
	/** Poster's RuneScape name. */
	String sender;
	/** Where the message came from: FC, CC, Guest CC, Public, or a Discord channel label. */
	String source;
	RaidType raidType;
	/** WDR experience tier (KC-gated), e.g. Learner/Standard/Advanced/Efficient/FFA/HM/HM Exp. */
	@Nullable
	String tier;
	/** Mode qualifier such as HM, CM, Entry, a ToA invocation ("350 invo"), "kit", or "remnant". */
	@Nullable
	String mode;
	/** Open spots like "+2", if the message contained one. */
	@Nullable
	String spots;
	/** Roles wanted/offered, e.g. "mdps/rdps" or "nfrz", if any. */
	@Nullable
	String roles;
	/** Party fill like "3/5", or a "duo"/"trio" qualifier. */
	@Nullable
	String partySize;
	/** World number, or 0 if none was mentioned. */
	int world;
	/** Region hint such as "eu"/"usw", if any. */
	@Nullable
	String region;
	/** Party-hub passphrase for joiners, distinct from the poster RuneScape name ({@code sender}). */
	@Nullable
	String host;
	/** Poster's highest raid KC for this raid (from OSRS hiscores), or 0 if unknown. */
	int kc;
	/** Classification: RAID (advert), LFG (looking), ROLE (offer), or JOIN (coming). */
	String kind;
	String message;
	Instant timestamp;
}
