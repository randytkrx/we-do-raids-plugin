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

		state.enter(fields);
		fields.put("spots", "+1");
		assertEquals("+2", state.confirmedSnapshot().get("spots"));

		final Map<String, String> snapshot = state.confirmedSnapshot();
		snapshot.put("spots", "+0");
		assertEquals("+2", state.confirmedSnapshot().get("spots"));

		state.confirm(snapshot);
		assertEquals("+0", state.confirmedSnapshot().get("spots"));
	}

	@Test
	public void beginAcceptsOnlyOneCurrentOperation()
	{
		final HostLiveState state = new HostLiveState();
		final long operation = state.begin();

		assertTrue(operation > 0);
		assertFalse(state.canStart());
		assertTrue(state.accepts(operation));
		assertEquals(-1, state.begin());

		state.complete(operation);
		assertTrue(state.canStart());
		assertFalse(state.accepts(operation));
	}

	@Test
	public void stopInvalidatesOperationAndPreventsReentry()
	{
		final HostLiveState state = new HostLiveState();
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("messageId", "message-id");
		state.enter(fields);
		final long operation = state.begin();

		state.stop();

		assertTrue(state.isStopped());
		assertFalse(state.canStart());
		assertFalse(state.accepts(operation));
		assertEquals("message-id", state.confirmedSnapshot().get("messageId"));
		assertEquals(-1, state.begin());
	}

	@Test
	public void exitClearsConfirmedFieldsAndInvalidatesOperation()
	{
		final HostLiveState state = new HostLiveState();
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("messageId", "message-id");
		state.enter(fields);
		final long operation = state.begin();

		state.exit();

		assertNull(state.confirmedSnapshot());
		assertTrue(state.canStart());
		assertFalse(state.accepts(operation));
	}
}
