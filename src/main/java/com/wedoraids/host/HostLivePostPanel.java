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
package com.wedoraids.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class HostLivePostPanel extends JPanel
{
	private final HostFormPanel.HostActions actions;
	private final HostLiveState liveState;
	private final HostInactivityGuard inactivityGuard;
	private final Consumer<Map<String, String>> displayedFieldsChanged;
	private final HostLivePostView view;
	private Map<String, String> displayedFields;

	HostLivePostPanel(HostFormPanel.HostActions actions, HostLiveState liveState,
		HostInactivityGuard inactivityGuard, Runnable beginEdit,
		Consumer<Map<String, String>> displayedFieldsChanged)
	{
		this.actions = actions;
		this.liveState = liveState;
		this.inactivityGuard = inactivityGuard;
		this.displayedFieldsChanged = displayedFieldsChanged;
		this.view = new HostLivePostView(inactivityGuard, this::fillRole, this::decrementSpot,
			beginEdit, this::close);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setVisible(false);
		add(view);
	}

	JLabel statusLabel()
	{
		return view.statusLabel();
	}

	void enterLivePost(Map<String, String> fields, String messageId)
	{
		displayedFields = new LinkedHashMap<>(fields);
		if (messageId != null && !messageId.isEmpty())
		{
			displayedFields.put("messageId", messageId);
		}
		displayedFieldsChanged.accept(displayedFields);
		liveState.enterLivePost(displayedFields);
		view.setStatus(" ", false);
		rebuildControls();
		inactivityGuard.enterLivePost();
	}

	void exitLivePost()
	{
		displayedFields = null;
		displayedFieldsChanged.accept(null);
		liveState.exitLivePost();
		inactivityGuard.exitLivePost();
		setVisible(false);
	}

	void fillRole(String role)
	{
		if (!canMutate())
		{
			return;
		}
		final Map<String, String> previous = liveState.confirmedFieldsSnapshot();
		final List<String> roles = currentRoles();
		roles.remove(role);
		if (roles.isEmpty())
		{
			displayedFields.remove("roles");
		}
		else
		{
			displayedFields.put("roles", String.join(", ", roles));
		}
		decrementSpotValue();
		afterSpotChange(previous);
	}

	void decrementSpot()
	{
		if (!canMutate())
		{
			return;
		}
		final Map<String, String> previous = liveState.confirmedFieldsSnapshot();
		decrementSpotValue();
		afterSpotChange(previous);
	}

	void close()
	{
		if (!canMutate())
		{
			return;
		}
		final Map<String, String> snapshot = liveState.confirmedFieldsSnapshot();
		close(snapshot, snapshot);
	}

	void closeForInactivity()
	{
		view.setStatus("No response, closing raid…", false);
		close();
	}

	void rebuildControls()
	{
		view.rebuild(displayedFields);
	}

	private List<String> currentRoles()
	{
		final List<String> roles = new ArrayList<>();
		final String raw = displayedFields == null ? null : displayedFields.get("roles");
		if (raw != null && !raw.isBlank())
		{
			for (String part : raw.split(","))
			{
				final String role = part.trim();
				if (!role.isEmpty())
				{
					roles.add(role);
				}
			}
		}
		return roles;
	}

	private void afterSpotChange(Map<String, String> previous)
	{
		final Map<String, String> candidate = new LinkedHashMap<>(displayedFields);
		if ("+0".equals(displayedFields.get("spots")))
		{
			view.setStatus("Party full, closing raid…", false);
			close(previous, candidate);
			return;
		}
		inactivityGuard.reset();
		pushUpdate(previous, candidate);
		rebuildControls();
	}

	private void decrementSpotValue()
	{
		final String spots = displayedFields.get("spots");
		if (spots != null && spots.startsWith("+"))
		{
			try
			{
				final int count = Math.max(0, Integer.parseInt(spots.substring(1)) - 1);
				displayedFields.put("spots", "+" + count);
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private boolean canMutate()
	{
		return liveState.canStartOperation() && displayedFields != null;
	}

	private void pushUpdate(Map<String, String> previous, Map<String, String> candidate)
	{
		final long operation = liveState.beginOperation();
		view.setStatus("Updating…", false);
		actions.update(new LinkedHashMap<>(candidate), message ->
		{
			if (!liveState.isCurrentOperation(operation))
			{
				return;
			}
			final boolean success = message.startsWith("Updated");
			if (success)
			{
				liveState.confirmLiveFields(candidate);
			}
			else
			{
				restore(previous);
			}
			liveState.completeOperation(operation);
			view.setStatus(message, !success);
		});
	}

	private void close(Map<String, String> previous, Map<String, String> candidate)
	{
		final long operation = liveState.beginOperation();
		view.setStatus("Closing…", false);
		actions.close(new LinkedHashMap<>(candidate), message ->
		{
			if (!liveState.isCurrentOperation(operation))
			{
				return;
			}
			final boolean success = message.startsWith("Closed");
			if (!success)
			{
				restore(previous);
			}
			liveState.completeOperation(operation);
			view.setStatus(message, !success);
		});
	}

	private void restore(Map<String, String> previous)
	{
		displayedFields = new LinkedHashMap<>(previous);
		displayedFieldsChanged.accept(displayedFields);
		rebuildControls();
	}
}
