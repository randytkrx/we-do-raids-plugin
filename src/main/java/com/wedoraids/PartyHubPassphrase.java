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

import java.util.Random;

final class PartyHubPassphrase
{
	private static final String[] SHORT_ANIMALS = {
		"cat", "dog", "owl", "fox", "bat", "rat", "hen", "cow", "ant", "bee", "elk",
		"eel", "ape", "koi", "pig", "ram", "jay", "pup", "hog", "boa", "doe", "emu",
		"gnu", "yak", "bird", "wolf", "bear", "crab", "frog", "toad", "moth", "swan",
		"dove", "hawk", "lynx", "mole", "newt", "puma", "seal", "wren",
	};
	private static final String[] SINGLE_ANIMALS = {
		"otter", "panda", "gecko", "koala", "lemur", "zebra", "hyena", "dingo", "tapir",
		"quail", "raven", "robin", "shark", "sloth", "snail", "squid", "tiger", "whale",
		"bison", "camel", "eagle", "rabbit", "badger", "donkey", "falcon", "jaguar",
		"ocelot", "possum", "toucan", "walrus", "weasel", "wombat",
	};

	private PartyHubPassphrase()
	{
	}

	static String generate()
	{
		final Random random = new Random();
		if (random.nextBoolean())
		{
			return SINGLE_ANIMALS[random.nextInt(SINGLE_ANIMALS.length)];
		}
		for (int attempt = 0; attempt < 10; attempt++)
		{
			final String first = SHORT_ANIMALS[random.nextInt(SHORT_ANIMALS.length)];
			final String second = SHORT_ANIMALS[random.nextInt(SHORT_ANIMALS.length)];
			if (!first.equals(second) && first.length() + second.length() <= 7)
			{
				return first + second;
			}
		}
		return SINGLE_ANIMALS[random.nextInt(SINGLE_ANIMALS.length)];
	}
}
