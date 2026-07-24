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

public final class PlayerNameNormalizer
{
	private PlayerNameNormalizer()
	{
	}

	public static String normalize(String s)
	{
		if (s == null)
		{
			return "";
		}
		// NBSP appears in Jagex names; fold it to a regular space before comparing.
		return stripTags(s).replace('\u00A0', ' ').toLowerCase().trim();
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
