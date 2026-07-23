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
