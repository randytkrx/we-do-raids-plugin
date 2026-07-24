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
