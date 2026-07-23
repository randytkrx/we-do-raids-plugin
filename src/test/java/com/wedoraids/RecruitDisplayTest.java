package com.wedoraids;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecruitDisplayTest
{
	@Test
	public void firstInt_returnsZero_whenValueExceedsIntegerRange()
	{
		assertEquals(0, RecruitDisplay.firstInt("2147483648"));
	}

	@Test
	public void firstInt_returnsZero_whenDigitRunIsMuchLongerThanIntegerRange()
	{
		assertEquals(0, RecruitDisplay.firstInt("999999999999999999999999999999999999999999999999999"));
	}

	@Test
	public void firstInt_returnsFirstEmbeddedInteger_whenValueFits()
	{
		assertEquals(350, RecruitDisplay.firstInt("ToA 350 invo"));
	}

	@Test
	public void teamTotal_returnsNamedAndNumericPartySizes()
	{
		assertEquals(3, RecruitDisplay.teamTotal("trio"));
		assertEquals(5, RecruitDisplay.teamTotal("5 man"));
	}

	@Test
	public void firstInt_returnsUnicodeDecimalNumber_whenTextUsesNonAsciiDigits()
	{
		assertEquals(3, RecruitDisplay.firstInt("team \u0663"));
	}

	@Test
	public void wdrTierNumber_returnsHighestApplicableTier()
	{
		assertEquals("T5", RecruitDisplay.wdrTierNumber(RaidType.COX, 500));
		assertEquals("T1", RecruitDisplay.wdrTierNumber(RaidType.TOB, 1));
	}

}
