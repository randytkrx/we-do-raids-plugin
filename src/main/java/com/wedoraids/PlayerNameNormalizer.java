package com.wedoraids;

final class PlayerNameNormalizer
{
	private PlayerNameNormalizer()
	{
	}

	static String normalize(String s)
	{
		if (s == null)
		{
			return "";
		}
		return stripTags(s).replace(' ', ' ').toLowerCase().trim();
	}

	private static String stripTags(String value)
	{
		StringBuilder stripped = null;
		int copyFrom = 0;
		int opening = value.indexOf('<');
		while (opening >= 0)
		{
			final int closing = value.indexOf('>', opening + 1);
			if (closing > opening + 1)
			{
				if (stripped == null)
				{
					stripped = new StringBuilder(value.length());
				}
				stripped.append(value, copyFrom, opening);
				copyFrom = closing + 1;
				opening = value.indexOf('<', copyFrom);
			}
			else
			{
				opening = value.indexOf('<', opening + 1);
			}
		}
		return stripped == null ? value : stripped.append(value, copyFrom, value.length()).toString();
	}
}
