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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class HostLiveStateTest
{
	@Test
	public void enterAndConfirmUseDefensiveSnapshots()
	{
		final HostLiveState state = new HostLiveState();
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("raid", "TOB");
		fields.put("spots", "+2");

		state.enterLivePost(fields);
		fields.put("spots", "+1");
		assertEquals("+2", state.confirmedFieldsSnapshot().get("spots"));

		final Map<String, String> snapshot = state.confirmedFieldsSnapshot();
		snapshot.put("spots", "+0");
		assertEquals("+2", state.confirmedFieldsSnapshot().get("spots"));

		state.confirmLiveFields(snapshot);
		assertEquals("+0", state.confirmedFieldsSnapshot().get("spots"));
	}

	@Test
	public void beginAcceptsOnlyOneCurrentOperation()
	{
		final HostLiveState state = new HostLiveState();
		final long operation = state.beginOperation();

		assertTrue(operation > 0);
		assertFalse(state.canStartOperation());
		assertTrue(state.isCurrentOperation(operation));
		assertEquals(-1, state.beginOperation());

		state.completeOperation(operation);
		assertTrue(state.canStartOperation());
		assertFalse(state.isCurrentOperation(operation));
	}

	@Test
	public void stopInvalidatesOperationAndPreventsReentry()
	{
		final HostLiveState state = new HostLiveState();
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("messageId", "message-id");
		state.enterLivePost(fields);
		final long operation = state.beginOperation();

		state.stop();

		assertTrue(state.isStopped());
		assertFalse(state.canStartOperation());
		assertFalse(state.isCurrentOperation(operation));
		assertEquals("message-id", state.confirmedFieldsSnapshot().get("messageId"));
		assertEquals(-1, state.beginOperation());
	}

	@Test
	public void exitClearsConfirmedFieldsAndInvalidatesOperation()
	{
		final HostLiveState state = new HostLiveState();
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("messageId", "message-id");
		state.enterLivePost(fields);
		final long operation = state.beginOperation();

		state.exitLivePost();

		assertNull(state.confirmedFieldsSnapshot());
		assertTrue(state.canStartOperation());
		assertFalse(state.isCurrentOperation(operation));
	}
}
