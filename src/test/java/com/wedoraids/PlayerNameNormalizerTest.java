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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerNameNormalizerTest
{
	@Test
	public void normalize_stripsMarkupAndNormalizesText()
	{
		assertEquals("", PlayerNameNormalizer.normalize(null));
		assertEquals("alice", PlayerNameNormalizer.normalize(" <col=ff0000>Alice</col> "));
		assertEquals("alice", PlayerNameNormalizer.normalize("<b><i>ALICE</i></b>"));
		assertEquals("<>alice", PlayerNameNormalizer.normalize("<>ALICE"));
		assertEquals("<col=ff0000alice", PlayerNameNormalizer.normalize("<col=ff0000ALICE"));
		assertEquals(">alice", PlayerNameNormalizer.normalize("<<>>ALICE"));
		assertEquals("alice bob", PlayerNameNormalizer.normalize("\u00a0ALICE\u00a0BOB\u00a0"));
		assertEquals("a  b", PlayerNameNormalizer.normalize(" A  B "));
		assertEquals("alice", PlayerNameNormalizer.normalize("<col=ff\n0000>ALICE</col>"));
	}

	@Test
	public void normalize_removesTagsNormalizesWhitespaceAndLowercases()
	{
		assertEquals("wdr host", PlayerNameNormalizer.normalize(" <col=ff0000>WDR\u00a0Host</col> "));
	}
}
