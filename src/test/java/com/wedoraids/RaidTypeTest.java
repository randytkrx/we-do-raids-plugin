package com.wedoraids;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RaidTypeTest
{
	@Test
	public void minKc_returnsTierRequirementAndZeroForUnknownTier()
	{
		assertEquals(500, RaidType.COX.minKc("CM Efficiency"));
		assertEquals(0, RaidType.TOB.minKc("unknown"));
	}
}
