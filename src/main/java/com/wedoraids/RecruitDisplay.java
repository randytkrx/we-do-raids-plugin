package com.wedoraids;

final class RecruitDisplay
{
	private static final int[] WDR_TIER_KC_COX = {0, 5, 25, 75, -1, 500};
	private static final int[] WDR_TIER_KC_TOB = {0, 1, 10, -1, 100, -1};
	private static final int[] WDR_TIER_KC_TOA = {0, 5, -1, -1, -1, -1};

	private RecruitDisplay()
	{
	}

	static String wdrTierNumber(RaidType raid, int kc)
	{
		if (kc <= 0)
		{
			return null;
		}
		final int[] bounds;
		switch (raid)
		{
			case COX:
				bounds = WDR_TIER_KC_COX;
				break;
			case TOB:
				bounds = WDR_TIER_KC_TOB;
				break;
			case TOA:
				bounds = WDR_TIER_KC_TOA;
				break;
			default:
				return null;
		}
		int tier = 0;
		for (int i = 0; i < bounds.length; i++)
		{
			if (bounds[i] >= 0 && kc >= bounds[i])
			{
				tier = i;
			}
		}
		return "T" + tier;
	}

	static int teamTotal(String partySize)
	{
		if (partySize == null)
		{
			return 0;
		}
		switch (partySize.toLowerCase().trim())
		{
			case "solo":
				return 1;
			case "duo":
				return 2;
			case "trio":
				return 3;
			case "quad":
				return 4;
			default:
				return firstInt(partySize);
		}
	}

	static int firstInt(String text)
	{
		if (text == null)
		{
			return 0;
		}
		int value = 0;
		boolean foundDigit = false;
		for (int i = 0; i < text.length(); i++)
		{
			final char character = text.charAt(i);
			if (Character.isDigit(character))
			{
				foundDigit = true;
				final int digit = Character.digit(character, 10);
				if (value > (Integer.MAX_VALUE - digit) / 10)
				{
					return 0;
				}
				value = value * 10 + digit;
			}
			else if (foundDigit)
			{
				return value;
			}
		}
		return value;
	}
}
