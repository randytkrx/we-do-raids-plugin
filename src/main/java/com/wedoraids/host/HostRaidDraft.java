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

import com.wedoraids.feed.RaidType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;

final class HostRaidDraft
{
	private final HostDependencies dependencies;
	private final Supplier<RaidType> selectedRaid;
	private final BiConsumer<String, Boolean> status;
	private final JComboBox<String> tier;
	private final JTextField world;
	private final JComboBox<String> spots;
	private final JComboBox<String> team;
	private final JCheckBox[] roleChecks;
	private final JTextField roles;
	private final JTextField scale;
	private final JTextField fc;
	private final JTextField layout;
	private final JTextField partyHub;
	private final JTextField description;
	private final BooleanSupplier layoutApplies;

	HostRaidDraft(HostDependencies dependencies, Supplier<RaidType> selectedRaid,
		BiConsumer<String, Boolean> status, JComboBox<String> tier, JTextField world,
		JComboBox<String> spots, JComboBox<String> team, JCheckBox mdps, JCheckBox rdps,
		JCheckBox nfrz, JCheckBox sfrz, JTextField roles, JTextField scale, JTextField fc,
		JTextField layout, JTextField partyHub, JTextField description, BooleanSupplier layoutApplies)
	{
		this.dependencies = dependencies;
		this.selectedRaid = selectedRaid;
		this.status = status;
		this.tier = tier;
		this.world = world;
		this.spots = spots;
		this.team = team;
		this.roleChecks = new JCheckBox[]{mdps, rdps, nfrz, sfrz};
		this.roles = roles;
		this.scale = scale;
		this.fc = fc;
		this.layout = layout;
		this.partyHub = partyHub;
		this.description = description;
		this.layoutApplies = layoutApplies;
	}

	Map<String, String> collect()
	{
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("raid", selectedRaid.get().name());
		if (tier.getSelectedItem() != null)
		{
			fields.put("tier", (String) tier.getSelectedItem());
		}
		if (!collectWorld(fields) || !collectCox(fields))
		{
			return null;
		}
		putIfPresent(fields, "size", (String) team.getSelectedItem());
		putIfPresent(fields, "spots", (String) spots.getSelectedItem());
		collectRoles(fields);
		if (layoutApplies.getAsBoolean())
		{
			String value = layout.getText().trim();
			if (value.isEmpty() && dependencies.coxLayout().get() != null)
			{
				value = dependencies.coxLayout().get().trim();
			}
			putIfPresent(fields, "layout", value);
		}
		putIfPresent(fields, "partyHub", partyHub.getText().trim());
		putIfPresent(fields, "desc", description.getText().trim());
		return fields;
	}

	private boolean collectWorld(Map<String, String> fields)
	{
		final String value = world.getText().trim();
		if (value.isEmpty())
		{
			return true;
		}
		if (!value.matches("\\d{1,3}"))
		{
			status.accept("World must be a number.", true);
			return false;
		}
		final String blocked = dependencies.worldBlockReason().apply(Integer.parseInt(value));
		if (blocked != null)
		{
			status.accept("W" + value + " is " + blocked + ", pick a different world.", true);
			return false;
		}
		fields.put("world", value);
		return true;
	}

	private boolean collectCox(Map<String, String> fields)
	{
		if (selectedRaid.get() != RaidType.COX)
		{
			return true;
		}
		final String value = scale.getText().trim();
		if (!value.isEmpty())
		{
			if (!value.matches("\\d{1,3}") || Integer.parseInt(value) > 100)
			{
				status.accept("Scale must be 0-100.", true);
				return false;
			}
			fields.put("scale", value);
		}
		putIfPresent(fields, "fc", fc.getText().trim());
		return true;
	}

	private void collectRoles(Map<String, String> fields)
	{
		final List<String> values = new ArrayList<>();
		if (selectedRaid.get() == RaidType.TOB)
		{
			final String[] names = {"mdps", "rdps", "nfrz", "sfrz"};
			for (int index = 0; index < roleChecks.length; index++)
			{
				if (roleChecks[index].isSelected())
				{
					values.add(names[index]);
				}
			}
		}
		final String extra = roles.getText().trim();
		if (!extra.isEmpty())
		{
			values.add(extra);
		}
		if (!values.isEmpty())
		{
			fields.put("roles", String.join(", ", values));
		}
	}

	private static void putIfPresent(Map<String, String> fields, String key, String value)
	{
		if (value != null && !value.isEmpty())
		{
			fields.put(key, value);
		}
	}
}
