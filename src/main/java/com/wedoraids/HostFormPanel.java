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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import net.runelite.client.ui.FontManager;

/**
 * Collapsible "host a raid" form. Gathers raid/tier/world/spots/roles and hands
 * them to a submitter, which posts them through the bridge into the right channel.
 */
class HostFormPanel extends JPanel
{
	/** Actions the form drives through the bridge: post a new call, edit it, or close it. */
	interface HostActions
	{
		void submit(Map<String, String> fields, Consumer<String> status);

		void update(Map<String, String> fields, Consumer<String> status);

		void close(Map<String, String> fields, Consumer<String> status);
	}

	private final HostActions actions;
	private final java.util.function.IntSupplier currentWorld;
	private final java.util.function.Supplier<String> coxLayout;
	private final java.util.function.Supplier<String> localIgn;
	private final java.util.function.ToIntFunction<RaidType> userKc;
	private final Runnable requestKc;
	private final Runnable onIdleWarn;
	/** Whether to auto-generate a party-hub passphrase when hosting (config toggle). */
	private final java.util.function.BooleanSupplier autoHub;
	/** Why a world can't be hosted on (PvP/High Risk/etc.), or null if it's fine. */
	private final java.util.function.IntFunction<String> worldBlockReason;

	private final JComboBox<String> raidCombo = new JComboBox<>(new String[]{"ToB", "CoX", "ToA"});
	private final JComboBox<String> tierCombo = new JComboBox<>(RaidType.TOB.getTiers());
	private final JTextField worldField = new JTextField();
	private final JComboBox<String> spotsCombo = new JComboBox<>(new String[]{"", "+1", "+2", "+3", "+4"});
	private final JComboBox<String> teamCombo = new JComboBox<>();
	private final JCheckBox mdps = new JCheckBox("mdps");
	private final JCheckBox rdps = new JCheckBox("rdps");
	private final JCheckBox nfrz = new JCheckBox("nfrz");
	private final JCheckBox sfrz = new JCheckBox("sfrz");
	private final JTextField rolesField = new JTextField();
	private final JTextField scaleField = new JTextField();
	private final JTextField fcField = new JTextField();
	private final JTextField layoutField = new JTextField();
	private final JTextField partyHubField = new JTextField();
	private final JTextField descField = new JTextField();
	private final JLabel statusLabel = new JLabel(" ");
	private JPanel roleCheckboxRow;
	private JPanel scaleFcRow;
	private JPanel layoutRow;
	/** Everything below the raid tabs; revealed once a raid is picked. */
	private final JPanel details = new JPanel();
	private final RaidTab[] raidTabs = new RaidTab[3];
	private boolean raidChosen;

	private final JPanel form = new JPanel();
	private final JPanel livePanel = new JPanel();
	private final JLabel liveStatus = new JLabel(" ");
	private final JButton toggle = new JButton("＋ Host a raid");
	private final JButton postButton = new JButton("Post to Discord");
	private final JButton cancelEditButton = new JButton("Cancel edit");
	private boolean expanded;
	/** True while re-editing an already-posted call (Save changes -> /update, not a new post). */
	private boolean editingLive;

	/** Fields from the last submission, reused when an acknowledgment enters live mode. */
	private Map<String, String> lastSubmittedFields;
	/** Working copy while a post is live (includes messageId); null when not live. */
	private Map<String, String> displayedLiveFields;
	private final HostLiveState liveState = new HostLiveState();

	// Inactivity guard: after IDLE_MS with no host action, prompt "still here?" for
	// PROMPT_SECONDS; no response auto-closes the party.
	private static final int IDLE_MS = 7 * 60 * 1000;
	private static final int PROMPT_SECONDS = 60;
	private final Timer idleTimer;
	private final Timer promptTimer;
	private final JPanel idleBanner = new JPanel();
	private final JLabel idleCountdown = new JLabel();
	private int promptRemaining;
	private boolean idlePromptActive;

	HostFormPanel(HostDependencies dependencies)
	{
		this.actions = dependencies.actions();
		this.onIdleWarn = dependencies.onIdleWarn();
		this.currentWorld = dependencies.currentWorld();
		this.coxLayout = dependencies.coxLayout();
		this.localIgn = dependencies.localIgn();
		this.userKc = dependencies.userKc();
		this.requestKc = dependencies.requestKc();
		this.autoHub = dependencies.autoHub();
		this.worldBlockReason = dependencies.worldBlockReason();

		setLayout(new BorderLayout(0, 4));
		setOpaque(false);

		WdrTheme.styleButton(toggle);
		toggle.addActionListener(e -> setExpanded(!expanded));
		add(toggle, BorderLayout.NORTH);

		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setOpaque(false);
		form.setVisible(false);

		WdrTheme.styleCombo(raidCombo);
		WdrTheme.styleCombo(tierCombo);
		WdrTheme.styleCombo(spotsCombo);
		WdrTheme.styleCombo(teamCombo);
		WdrTheme.styleField(worldField);
		WdrTheme.styleField(rolesField);
		WdrTheme.styleField(scaleField);
		WdrTheme.styleField(fcField);
		WdrTheme.styleField(layoutField);
		WdrTheme.styleField(partyHubField);
		WdrTheme.styleField(descField);

		// Step 1: pick the raid via colored tab buttons; the rest reveals after a pick.
		JPanel raidRow = new JPanel(new GridLayout(1, 3, 4, 0));
		raidRow.setOpaque(false);
		raidTabs[0] = new RaidTab(RaidType.TOB, 0);
		raidTabs[1] = new RaidTab(RaidType.COX, 1);
		raidTabs[2] = new RaidTab(RaidType.TOA, 2);
		for (RaidTab t : raidTabs)
		{
			raidRow.add(t);
		}
		raidRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		raidRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		form.add(raidRow);
		form.add(Box.createVerticalStrut(4));

		// Step 2: the details, two fields per row to keep the form short.
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setOpaque(false);
		details.setVisible(false);
		details.setAlignmentX(Component.LEFT_ALIGNMENT);

		details.add(pair(labeled("Tier", tierCombo), labeled("World", worldField)));
		details.add(pair(labeled("Team size", teamCombo), labeled("Open spots", spotsCombo)));
		// CoX-only pair: scale + friends chat.
		scaleFcRow = pair(labeled("Scale (0-100)", scaleField), labeled("Friends chat", fcField));
		details.add(scaleFcRow);
		// CoX layout, auto-scouted from the raids plugin; shown only for non-CM CoX.
		layoutRow = labeled("Layout (auto)", layoutField);
		details.add(layoutRow);

		JPanel roleRow = new JPanel(new GridLayout(2, 2));
		roleRow.setOpaque(false);
		for (JCheckBox cb : new JCheckBox[]{mdps, rdps, nfrz, sfrz})
		{
			cb.setOpaque(false);
			cb.setForeground(WdrTheme.TEXT);
			cb.setFont(FontManager.getRunescapeSmallFont());
			roleRow.add(cb);
		}
		// The mdps/rdps/nfrz/sfrz checkboxes are ToB-only; hidden for CoX/ToA.
		roleCheckboxRow = labeled("Roles you need (looking for)", roleRow);
		details.add(roleCheckboxRow);
		details.add(labeled("Other roles you need", rolesField));
		details.add(labeled("Party hub (optional)", partyHubField));
		details.add(labeled("Description (e.g. pogstack, max only)", descField));

		raidCombo.addActionListener(e ->
		{
			refreshTiers();
			refreshTeamOptions();
			updateRoleVisibility();
			updateLayoutVisibility();
			updateScaleVisibility();
		});
		tierCombo.addActionListener(e -> updateLayoutVisibility());
		refreshTeamOptions();
		updateRoleVisibility();
		updateLayoutVisibility();
		updateScaleVisibility();

		details.add(Box.createVerticalStrut(2));
		WdrTheme.styleButton(postButton);
		postButton.addActionListener(e -> doSubmit());
		postButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(postButton);

		// Only visible while editing a live post — bail out without saving.
		WdrTheme.styleButton(cancelEditButton);
		cancelEditButton.addActionListener(e -> cancelEdit());
		cancelEditButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		cancelEditButton.setVisible(false);
		details.add(cancelEditButton);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(WdrTheme.TEXT_DIM);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		details.add(statusLabel);

		form.add(details);

		livePanel.setLayout(new BoxLayout(livePanel, BoxLayout.Y_AXIS));
		livePanel.setOpaque(false);
		livePanel.setVisible(false);
		liveStatus.setFont(FontManager.getRunescapeSmallFont());
		liveStatus.setForeground(WdrTheme.TEXT_DIM);
		liveStatus.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
		center.setOpaque(false);
		center.add(form);
		center.add(livePanel);
		add(center, BorderLayout.CENTER);

		idleTimer = new Timer(IDLE_MS, e -> showActivityPrompt());
		idleTimer.setRepeats(false);
		promptTimer = new Timer(1000, e -> tickPrompt());
		buildIdleBanner();
	}

	private void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		toggle.setText((expanded ? "－" : "＋") + " Host a raid");
		// While a post is live the live controls own the panel (unless mid-edit,
		// when the form is showing the post's fields); keep the other view hidden.
		form.setVisible(expanded && (displayedLiveFields == null || editingLive));
		livePanel.setVisible(expanded && displayedLiveFields != null && !editingLive);
		if (expanded && worldField.getText().trim().isEmpty())
		{
			// Default the world box to the world you're currently on.
			final int world = currentWorld.getAsInt();
			if (world > 0)
			{
				worldField.setText(Integer.toString(world));
			}
		}
		if (expanded)
		{
			requestKc.run(); // refresh KC so the tier list reflects what you qualify for
			updateLayoutVisibility();
			updateScaleVisibility();
		}
		revalidate();
		repaint();
	}

	private RaidType selectedRaid()
	{
		switch (raidCombo.getSelectedIndex())
		{
			case 1:
				return RaidType.COX;
			case 2:
				return RaidType.TOA;
			default:
				return RaidType.TOB;
		}
	}

	/** Called by the plugin once KC has loaded, to re-filter the offered tiers. */
	void refreshTiersPublic()
	{
		refreshTiers();
	}

	/** Stops the inactivity timers; called on plugin shutdown so they can't fire into a dead panel. */
	void stopTimers()
	{
		liveState.stop();
		idleTimer.stop();
		promptTimer.stop();
	}

	private void refreshTiers()
	{
		final RaidType raid = selectedRaid();
		final int kc = userKc.applyAsInt(raid);
		final Object previous = tierCombo.getSelectedItem();
		tierCombo.removeAllItems();
		for (String t : raid.getTiers())
		{
			// kc < 0 means "not loaded yet" — show everything until it arrives.
			if (kc < 0 || raid.minKc(t) <= kc)
			{
				tierCombo.addItem(t);
			}
		}
		if (tierCombo.getItemCount() == 0 && raid.getTiers().length > 0)
		{
			tierCombo.addItem(raid.getTiers()[0]);
		}
		if (previous != null)
		{
			tierCombo.setSelectedItem(previous);
		}
	}

	/** Total-party-size options per raid: ToB 2-5, CoX/ToA 1-8 (ToA caps at 8). */
	private void refreshTeamOptions()
	{
		final RaidType raid = selectedRaid();
		final Object prev = teamCombo.getSelectedItem();
		teamCombo.removeAllItems();
		teamCombo.addItem("");
		final int min = raid == RaidType.TOB ? 2 : 1;
		final int max = raid == RaidType.TOB ? 5 : 8;
		for (int i = min; i <= max; i++)
		{
			teamCombo.addItem(String.valueOf(i));
		}
		if (prev != null)
		{
			teamCombo.setSelectedItem(prev);
		}
	}

	/** The role checkboxes only apply to ToB; hide them for CoX/ToA. */
	private void updateRoleVisibility()
	{
		roleCheckboxRow.setVisible(selectedRaid() == RaidType.TOB);
		revalidate();
		repaint();
	}

	/** Scale and friends-chat fields only apply to CoX. FC defaults to your IGN. */
	private void updateScaleVisibility()
	{
		final boolean cox = selectedRaid() == RaidType.COX;
		scaleFcRow.setVisible(cox);
		if (cox && fcField.getText().trim().isEmpty())
		{
			final String ign = localIgn.get();
			if (ign != null && !ign.isEmpty())
			{
				fcField.setText(ign);
			}
		}
		revalidate();
		repaint();
	}

	/** A raid tab was clicked: select it (drives the hidden combo) and reveal the details. */
	private void selectRaidTab(int index)
	{
		raidChosen = true;
		raidCombo.setSelectedIndex(index); // fires the listener -> tiers/teams/visibility
		details.setVisible(true);
		updateRaidTabs();
		// Auto party hub: suggest a short animal passphrase if the field is empty.
		if (autoHub.getAsBoolean() && partyHubField.getText().trim().isEmpty())
		{
			partyHubField.setText(generatePartyHub());
		}
		revalidate();
		repaint();
	}

	// Short animal words for the auto party hub: either one mid-length animal ("otter")
	// or two short ones glued together ("catdog", "foxowl"), always <= 7 characters.
	private static final String[] HUB_SHORT = {
		"cat", "dog", "owl", "fox", "bat", "rat", "hen", "cow", "ant", "bee", "elk",
		"eel", "ape", "koi", "pig", "ram", "jay", "pup", "hog", "boa", "doe", "emu",
		"gnu", "yak", "bird", "wolf", "bear", "crab", "frog", "toad", "moth", "swan",
		"dove", "hawk", "lynx", "mole", "newt", "puma", "seal", "wren",
	};
	private static final String[] HUB_SINGLE = {
		"otter", "panda", "gecko", "koala", "lemur", "zebra", "hyena", "dingo", "tapir",
		"quail", "raven", "robin", "shark", "sloth", "snail", "squid", "tiger", "whale",
		"bison", "camel", "eagle", "rabbit", "badger", "donkey", "falcon", "jaguar",
		"ocelot", "possum", "toucan", "walrus", "weasel", "wombat",
	};

	/** A random, memorable party-hub passphrase, max 7 chars (e.g. "catdog", "walrus"). */
	static String generatePartyHub()
	{
		final java.util.Random r = new java.util.Random();
		if (r.nextBoolean())
		{
			return HUB_SINGLE[r.nextInt(HUB_SINGLE.length)];
		}
		// Two short animals glued together, capped at 7 chars total.
		for (int i = 0; i < 10; i++)
		{
			final String a = HUB_SHORT[r.nextInt(HUB_SHORT.length)];
			final String b = HUB_SHORT[r.nextInt(HUB_SHORT.length)];
			if (!a.equals(b) && a.length() + b.length() <= 7)
			{
				return a + b;
			}
		}
		return HUB_SINGLE[r.nextInt(HUB_SINGLE.length)];
	}

	/** Refresh each tab's text color to reflect the selection. */
	private void updateRaidTabs()
	{
		for (RaidTab t : raidTabs)
		{
			if (t != null)
			{
				t.setForeground(t.isSelectedTab() ? t.raid.getColor() : WdrTheme.TEXT_DIM);
				t.repaint();
			}
		}
	}

	/** Two labeled fields side by side, to keep the form short. */
	private static JPanel pair(JPanel left, JPanel right)
	{
		JPanel p = new JPanel(new GridLayout(1, 2, 6, 0));
		p.setOpaque(false);
		p.add(left);
		p.add(right);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	/** A rounded raid tab button; colored with its raid's identity color when selected. */
	private final class RaidTab extends JButton
	{
		private final RaidType raid;
		private final int index;

		RaidTab(RaidType raid, int index)
		{
			super(raid.getDisplayName());
			this.raid = raid;
			this.index = index;
			setFont(FontManager.getRunescapeSmallFont());
			setForeground(WdrTheme.TEXT_DIM);
			setFocusPainted(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setCursor(new Cursor(Cursor.HAND_CURSOR));
			setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
			addActionListener(e -> selectRaidTab(index));
		}

		boolean isSelectedTab()
		{
			return raidChosen && raidCombo.getSelectedIndex() == index;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final boolean sel = isSelectedTab();
			final Color rc = raid.getColor();
			// Selected: a dark tint of the raid color with a colored outline; else neutral.
			final Color fill = sel
				? new Color(rc.getRed() / 5 + 18, rc.getGreen() / 5 + 18, rc.getBlue() / 5 + 18)
				: (getModel().isRollover() ? WdrTheme.HOVER : WdrTheme.FIELD);
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
			g2.setColor(sel ? rc : WdrTheme.BORDER);
			g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	private boolean layoutApplies()
	{
		final Object tier = tierCombo.getSelectedItem();
		// Only non-CM CoX has a scoutable layout.
		return selectedRaid() == RaidType.COX && tier != null && !tier.toString().contains("CM");
	}

	/** A new CoX raid was scouted — overwrite the auto layout field with the fresh layout. */
	void refreshCoxLayout()
	{
		if (!layoutApplies())
		{
			return;
		}
		final String layout = coxLayout.get();
		if (layout != null && !layout.isEmpty())
		{
			layoutField.setText(layout);
			revalidate();
			repaint();
		}
	}

	/** The CoX layout row shows only for non-CM CoX; auto-fills from the last scout. */
	private void updateLayoutVisibility()
	{
		final boolean show = layoutApplies();
		layoutRow.setVisible(show);
		if (show && layoutField.getText().trim().isEmpty())
		{
			final String layout = coxLayout.get();
			if (layout != null && !layout.isEmpty())
			{
				layoutField.setText(layout);
			}
		}
		revalidate();
		repaint();
	}

	private String raidCode()
	{
		return selectedRaid().name();
	}

	private void doSubmit()
	{
		if (!liveState.canStartOperation())
		{
			return;
		}
		final Map<String, String> fields = collectValidatedFields();
		if (fields == null)
		{
			return;
		}

		if (editingLive && displayedLiveFields != null)
		{
			// Saving edits to an already-posted call: push an /update, then return to live mode.
			final Map<String, String> update = new LinkedHashMap<>(fields);
			update.put("messageId", displayedLiveFields.get("messageId"));
			final long operation = liveState.beginOperation();
			setStatus("Saving…", false);
			actions.update(update, msg ->
			{
				if (!liveState.isCurrentOperation(operation))
				{
					return;
				}
				liveState.completeOperation(operation);
				setStatus(msg, !msg.startsWith("Updated"));
				if (msg.startsWith("Updated"))
				{
					finishEdit(fields);
				}
			});
			return;
		}

		setStatus("Posting…", false);
		// Remember what we posted so the live edit controls can decrement from it.
		final Map<String, String> submitted = new LinkedHashMap<>(fields);
		this.lastSubmittedFields = new LinkedHashMap<>(submitted);
		final long operation = liveState.beginOperation();
		// Convention: the submitter reports success with a message starting "Posted".
		actions.submit(submitted, msg ->
		{
			if (!liveState.isCurrentOperation(operation))
			{
				return;
			}
			liveState.completeOperation(operation);
			setStatus(msg, !msg.startsWith("Posted"));
		});
	}

	private Map<String, String> collectValidatedFields()
	{
		final Map<String, String> fields = new LinkedHashMap<>();
		fields.put("raid", raidCode());
		if (tierCombo.getSelectedItem() != null)
		{
			fields.put("tier", (String) tierCombo.getSelectedItem());
		}

		final String world = worldField.getText().trim();
		if (!world.isEmpty())
		{
			if (!world.matches("\\d{1,3}"))
			{
				setStatus("World must be a number.", true);
				return null;
			}
			// No recruiting on PvP/High Risk/BH/LMS/etc. worlds (or nonexistent ones).
			final String blocked = worldBlockReason.apply(Integer.parseInt(world));
			if (blocked != null)
			{
				setStatus("W" + world + " is " + blocked + " — pick a different world.", true);
				return null;
			}
			fields.put("world", world);
		}

		final String team = (String) teamCombo.getSelectedItem();
		if (team != null && !team.isEmpty())
		{
			fields.put("size", team);
		}

		final String spots = (String) spotsCombo.getSelectedItem();
		if (spots != null && !spots.isEmpty())
		{
			fields.put("spots", spots);
		}

		final List<String> roles = new ArrayList<>();
		if (selectedRaid() == RaidType.TOB)
		{
			if (mdps.isSelected())
			{
				roles.add("mdps");
			}
			if (rdps.isSelected())
			{
				roles.add("rdps");
			}
			if (nfrz.isSelected())
			{
				roles.add("nfrz");
			}
			if (sfrz.isSelected())
			{
				roles.add("sfrz");
			}
		}
		final String extra = rolesField.getText().trim();
		if (!extra.isEmpty())
		{
			roles.add(extra);
		}
		if (!roles.isEmpty())
		{
			fields.put("roles", String.join(", ", roles));
		}

		if (selectedRaid() == RaidType.COX)
		{
			final String scale = scaleField.getText().trim();
			if (!scale.isEmpty())
			{
				if (!scale.matches("\\d{1,3}") || Integer.parseInt(scale) > 100)
				{
					setStatus("Scale must be 0-100.", true);
					return null;
				}
				fields.put("scale", scale);
			}
			final String fc = fcField.getText().trim();
			if (!fc.isEmpty())
			{
				fields.put("fc", fc);
			}
		}

		if (layoutApplies())
		{
			String layout = layoutField.getText().trim();
			if (layout.isEmpty() && coxLayout.get() != null)
			{
				layout = coxLayout.get().trim();
			}
			if (!layout.isEmpty())
			{
				fields.put("layout", layout);
			}
		}

		final String partyHub = partyHubField.getText().trim();
		if (!partyHub.isEmpty())
		{
			fields.put("partyHub", partyHub);
		}

		final String desc = descField.getText().trim();
		if (!desc.isEmpty())
		{
			fields.put("desc", desc);
		}
		return fields;
	}

	/** Re-open the form pre-filled with the live post's fields so the host can change details. */
	private void beginEdit()
	{
		if (!liveState.canStartOperation() || displayedLiveFields == null)
		{
			return;
		}
		editingLive = true;
		// Editing IS activity — pause the inactivity guard so it can't auto-close the
		// raid under the host mid-edit; it re-arms when they save or cancel.
		idleTimer.stop();
		promptTimer.stop();
		idlePromptActive = false;
		populateForm(displayedLiveFields);
		postButton.setText("Save changes");
		cancelEditButton.setVisible(true);
		// The channel can't move, so lock the raid/tier while editing an existing post.
		raidCombo.setEnabled(false);
		tierCombo.setEnabled(false);
		setRaidTabsEnabled(false);
		livePanel.setVisible(false);
		form.setVisible(true);
		setStatus("Editing your live raid — change anything, then Save changes.", false);
		revalidate();
		repaint();
	}

	/** Abandon an in-progress edit and return to the live controls unchanged. */
	private void cancelEdit()
	{
		if (!liveState.canStartOperation() || !editingLive)
		{
			return;
		}
		resetHostFormControls();
		setStatus(" ", false);
		form.setVisible(false);
		livePanel.setVisible(true);
		resetIdle(); // re-arm the inactivity guard we paused for the edit
		revalidate();
		repaint();
	}

	private void setRaidTabsEnabled(boolean enabled)
	{
		for (RaidTab t : raidTabs)
		{
			if (t != null)
			{
				t.setEnabled(enabled);
			}
		}
	}

	private void resetHostFormControls()
	{
		editingLive = false;
		postButton.setText("Post to Discord");
		cancelEditButton.setVisible(false);
		raidCombo.setEnabled(true);
		tierCombo.setEnabled(true);
		setRaidTabsEnabled(true);
	}

	/** After a successful edit-save, restore the live controls with the new field values. */
	private void finishEdit(Map<String, String> fields)
	{
		resetHostFormControls();
		final String messageId = displayedLiveFields != null ? displayedLiveFields.get("messageId") : null;
		lastSubmittedFields = new LinkedHashMap<>(fields);
		form.setVisible(false);
		enterLivePost(messageId);
	}

	/** Fill the form controls from a saved field map (used when re-editing a live post). */
	private void populateForm(Map<String, String> f)
	{
		final String raid = f.getOrDefault("raid", "TOB");
		raidCombo.setSelectedIndex(raid.equals("COX") ? 1 : raid.equals("TOA") ? 2 : 0);
		// Editing an existing post: the raid is known, so show the details + light the tab.
		raidChosen = true;
		details.setVisible(true);
		updateRaidTabs();
		refreshTiers();
		if (f.get("tier") != null)
		{
			tierCombo.setSelectedItem(f.get("tier"));
		}
		worldField.setText(f.getOrDefault("world", ""));
		refreshTeamOptions();
		teamCombo.setSelectedItem(f.getOrDefault("size", ""));
		spotsCombo.setSelectedItem(f.getOrDefault("spots", ""));

		mdps.setSelected(false);
		rdps.setSelected(false);
		nfrz.setSelected(false);
		sfrz.setSelected(false);
		final List<String> extras = new ArrayList<>();
		if (f.get("roles") != null)
		{
			for (String part : f.get("roles").split(","))
			{
				final String r = part.trim().toLowerCase();
				switch (r)
				{
					case "mdps": mdps.setSelected(true); break;
					case "rdps": rdps.setSelected(true); break;
					case "nfrz": nfrz.setSelected(true); break;
					case "sfrz": sfrz.setSelected(true); break;
					default: if (!r.isEmpty()) extras.add(part.trim());
				}
			}
		}
		rolesField.setText(String.join(", ", extras));
		scaleField.setText(f.getOrDefault("scale", ""));
		fcField.setText(f.getOrDefault("fc", ""));
		layoutField.setText(f.getOrDefault("layout", ""));
		partyHubField.setText(f.getOrDefault("partyHub", ""));
		descField.setText(f.getOrDefault("desc", ""));

		updateRoleVisibility();
		updateScaleVisibility();
		updateLayoutVisibility();
	}

	// --- Live post mode: after a successful host, the form is replaced by edit controls. ---

	/** Enter live mode for a just-posted call; {@code messageId} identifies it to the bridge. */
	void enterLivePost(String messageId)
	{
		if (liveState.isStopped() || lastSubmittedFields == null)
		{
			return;
		}
		displayedLiveFields = new LinkedHashMap<>(lastSubmittedFields);
		if (messageId != null && !messageId.isEmpty())
		{
			displayedLiveFields.put("messageId", messageId);
		}
		liveState.enterLivePost(displayedLiveFields);
		form.setVisible(false);
		livePanel.setVisible(true);
		setLiveStatus(" ", false);
		idlePromptActive = false;
		rebuildLiveControls();
		resetIdle(); // start the 7-minute inactivity countdown
		revalidate();
		repaint();
	}

	/** Leave live mode (raid closed) and restore the normal form. */
	void exitLivePost()
	{
		displayedLiveFields = null;
		liveState.exitLivePost();
		idlePromptActive = false;
		resetHostFormControls();
		idleTimer.stop();
		promptTimer.stop();
		livePanel.setVisible(false);
		form.setVisible(expanded);
		revalidate();
		repaint();
	}

	private List<String> currentRoles()
	{
		final List<String> roles = new ArrayList<>();
		final String raw = displayedLiveFields == null ? null : displayedLiveFields.get("roles");
		if (raw != null && !raw.isBlank())
		{
			for (String part : raw.split(","))
			{
				final String r = part.trim();
				if (!r.isEmpty())
				{
					roles.add(r);
				}
			}
		}
		return roles;
	}

	private void rebuildLiveControls()
	{
		livePanel.removeAll();

		// When idle, the "still hosting?" banner sits above the controls.
		if (idlePromptActive)
		{
			livePanel.add(idleBanner);
			livePanel.add(Box.createVerticalStrut(6));
		}

		// A card with a green top accent that groups the live edit controls.
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(WdrTheme.CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 0, 0, 0, WdrTheme.GREEN),
			BorderFactory.createEmptyBorder(9, 10, 10, 10)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Header: which raid is live, then the open-spots chip below.
		final String spots = displayedLiveFields.get("spots");
		JLabel title = new JLabel("Your " + raidLabel(displayedLiveFields.get("raid")) + " raid is live");
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(WdrTheme.TEXT);
		fullWidth(title);
		card.add(title);
		card.add(Box.createVerticalStrut(4));

		JLabel spotsChip = new JLabel((spots == null || spots.isEmpty() ? "—" : spots) + " open");
		spotsChip.setFont(FontManager.getRunescapeSmallFont());
		spotsChip.setForeground(WdrTheme.TEXT);
		spotsChip.setOpaque(true);
		spotsChip.setBackground(WdrTheme.FIELD);
		spotsChip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(WdrTheme.BORDER),
			BorderFactory.createEmptyBorder(1, 8, 1, 8)));
		card.add(row(spotsChip));
		card.add(Box.createVerticalStrut(9));

		final List<String> roles = currentRoles();
		if (!roles.isEmpty())
		{
			// One short instruction, then the roles in a compact grid (not a tall stack).
			JLabel hint = new JLabel("Tap a role when it's filled:");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(WdrTheme.TEXT_DIM);
			fullWidth(hint);
			card.add(hint);
			card.add(Box.createVerticalStrut(5));

			JPanel grid = new JPanel(new GridLayout(0, Math.min(2, roles.size()), 5, 5));
			grid.setOpaque(false);
			for (String role : roles)
			{
				WdrButton b = new WdrButton(role, WdrButton.Variant.PRIMARY);
				b.addActionListener(e -> fillRole(role));
				grid.add(b);
			}
			fullWidth(grid);
			card.add(grid);
			card.add(Box.createVerticalStrut(6));

			// Secondary: a join that isn't one of the listed roles.
			WdrButton other = new WdrButton("Filled another spot", WdrButton.Variant.GHOST);
			other.addActionListener(e -> decrementSpot());
			fullWidth(other);
			card.add(other);
		}
		else
		{
			JLabel hint = new JLabel("Someone joined? Drop a spot:");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(WdrTheme.TEXT_DIM);
			fullWidth(hint);
			card.add(hint);
			card.add(Box.createVerticalStrut(5));

			WdrButton minusSpot = new WdrButton("−1 open spot", WdrButton.Variant.PRIMARY);
			minusSpot.addActionListener(e -> decrementSpot());
			fullWidth(minusSpot);
			card.add(minusSpot);
		}

		card.add(Box.createVerticalStrut(10));

		WdrButton editButton = new WdrButton("Edit details", WdrButton.Variant.GHOST);
		editButton.addActionListener(e -> beginEdit());
		fullWidth(editButton);
		card.add(editButton);
		card.add(Box.createVerticalStrut(6));

		WdrButton closeButton = new WdrButton("Close raid", WdrButton.Variant.DANGER);
		closeButton.addActionListener(e -> doClose());
		fullWidth(closeButton);
		card.add(closeButton);

		liveStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(Box.createVerticalStrut(6));
		card.add(liveStatus);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		livePanel.add(card);
		livePanel.revalidate();
		livePanel.repaint();
	}

	/** A left-aligned, non-stretching horizontal row of components. */
	private static JPanel row(Component... items)
	{
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.setOpaque(false);
		for (Component c : items)
		{
			p.add(c);
		}
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	/** Make a component span the panel width without growing vertically. */
	private static void fullWidth(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
	}

	private static String raidLabel(String code)
	{
		if (code == null)
		{
			return "";
		}
		switch (code.toUpperCase())
		{
			case "TOB":
				return "ToB";
			case "COX":
				return "CoX";
			case "TOA":
				return "ToA";
			default:
				return code;
		}
	}

	// --- Inactivity guard --------------------------------------------------------

	/** Builds the amber "still hosting?" banner shown once the idle timer fires. */
	private void buildIdleBanner()
	{
		idleBanner.setLayout(new BoxLayout(idleBanner, BoxLayout.Y_AXIS));
		idleBanner.setBackground(new Color(46, 34, 18));
		idleBanner.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(214, 170, 80)),
			BorderFactory.createEmptyBorder(8, 10, 10, 10)));
		idleBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel q = new JLabel("Still hosting this raid?");
		q.setFont(FontManager.getRunescapeSmallFont());
		q.setForeground(new Color(240, 210, 150));
		fullWidth(q);
		idleBanner.add(q);
		idleBanner.add(Box.createVerticalStrut(2));

		idleCountdown.setFont(FontManager.getRunescapeSmallFont());
		idleCountdown.setForeground(WdrTheme.TEXT_DIM);
		fullWidth(idleCountdown);
		idleBanner.add(idleCountdown);
		idleBanner.add(Box.createVerticalStrut(7));

		WdrButton here = new WdrButton("I'm here — keep it open", WdrButton.Variant.PRIMARY);
		here.addActionListener(e -> resetIdle());
		fullWidth(here);
		idleBanner.add(here);

		idleBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, idleBanner.getPreferredSize().height));
	}

	/** Idle timer fired: start the 60-second "still here?" countdown. */
	private void showActivityPrompt()
	{
		if (liveState.isStopped() || displayedLiveFields == null)
		{
			return;
		}
		idlePromptActive = true;
		promptRemaining = PROMPT_SECONDS;
		idleCountdown.setText("Auto-closing in " + promptRemaining + "s…");
		promptTimer.restart();
		rebuildLiveControls();
		if (onIdleWarn != null)
		{
			onIdleWarn.run(); // optional desktop notification (toggleable in settings)
		}
	}

	private void tickPrompt()
	{
		if (liveState.isStopped())
		{
			return;
		}
		promptRemaining--;
		if (promptRemaining <= 0)
		{
			promptTimer.stop();
			idlePromptActive = false;
			setLiveStatus("No response — closing raid…", false);
			doClose();
			// If the close fails the post is still up; re-arm so we prompt again later.
			if (!liveState.isStopped())
			{
				idleTimer.restart();
			}
			return;
		}
		idleCountdown.setText("Auto-closing in " + promptRemaining + "s…");
	}

	/** Host activity (or "I'm here"): clear any prompt and restart the idle countdown. */
	private void resetIdle()
	{
		if (liveState.isStopped())
		{
			return;
		}
		promptTimer.stop();
		final boolean wasPrompting = idlePromptActive;
		idlePromptActive = false;
		if (displayedLiveFields != null)
		{
			idleTimer.restart();
		}
		if (wasPrompting)
		{
			rebuildLiveControls();
		}
	}

	/** A role showed up: drop it, decrement the open spots, and push the edit. */
	private void fillRole(String role)
	{
		if (!canMutateLive())
		{
			return;
		}
		final Map<String, String> previous = liveState.confirmedFieldsSnapshot();
		final List<String> roles = currentRoles();
		roles.remove(role);
		if (roles.isEmpty())
		{
			displayedLiveFields.remove("roles");
		}
		else
		{
			displayedLiveFields.put("roles", String.join(", ", roles));
		}
		decrementSpotValue();
		afterSpotChange(previous);
	}

	private void decrementSpot()
	{
		if (!canMutateLive())
		{
			return;
		}
		final Map<String, String> previous = liveState.confirmedFieldsSnapshot();
		decrementSpotValue();
		afterSpotChange(previous);
	}

	/** Shared tail: full party ("+0") auto-closes; otherwise push the edit and re-arm idle. */
	private void afterSpotChange(Map<String, String> previous)
	{
		if (displayedLiveFields == null)
		{
			return;
		}
		final Map<String, String> candidate = new LinkedHashMap<>(displayedLiveFields);
		if ("+0".equals(displayedLiveFields.get("spots")))
		{
			setLiveStatus("Party full — closing raid…", false);
			doClose(previous, candidate);
			return;
		}
		resetIdle();
		pushUpdate(previous, candidate);
		rebuildLiveControls();
	}

	private void decrementSpotValue()
	{
		final String spots = displayedLiveFields.get("spots");
		if (spots != null && spots.startsWith("+"))
		{
			try
			{
				final int n = Math.max(0, Integer.parseInt(spots.substring(1)) - 1);
				displayedLiveFields.put("spots", "+" + n);
			}
			catch (NumberFormatException ignored)
			{
				// leave the spots field untouched if it isn't a "+N" value
			}
		}
	}

	private boolean canMutateLive()
	{
		return liveState.canStartOperation() && displayedLiveFields != null;
	}

	private void pushUpdate(Map<String, String> previous, Map<String, String> candidate)
	{
		final long operation = liveState.beginOperation();
		setLiveStatus("Updating…", false);
		actions.update(new LinkedHashMap<>(candidate), msg ->
		{
			if (!liveState.isCurrentOperation(operation))
			{
				return;
			}
			final boolean success = msg.startsWith("Updated");
			if (success)
			{
				liveState.confirmLiveFields(candidate);
			}
			else
			{
				displayedLiveFields = new LinkedHashMap<>(previous);
				rebuildLiveControls();
			}
			liveState.completeOperation(operation);
			setLiveStatus(msg, !success);
		});
	}

	private void doClose()
	{
		if (!canMutateLive())
		{
			return;
		}
		final Map<String, String> snapshot = liveState.confirmedFieldsSnapshot();
		doClose(snapshot, snapshot);
	}

	private void doClose(Map<String, String> previous, Map<String, String> candidate)
	{
		final long operation = liveState.beginOperation();
		setLiveStatus("Closing…", false);
		actions.close(new LinkedHashMap<>(candidate), msg ->
		{
			if (!liveState.isCurrentOperation(operation))
			{
				return;
			}
			final boolean success = msg.startsWith("Closed");
			if (!success)
			{
				displayedLiveFields = new LinkedHashMap<>(previous);
				rebuildLiveControls();
			}
			liveState.completeOperation(operation);
			setLiveStatus(msg, !success);
		});
	}

	private void setLiveStatus(String message, boolean error)
	{
		liveStatus.setText("<html><body style='width:180px'>" + escapeHtml(message) + "</body></html>");
		liveStatus.setForeground(error ? WdrTheme.ERROR : WdrTheme.TEXT_DIM);
	}

	private void setStatus(String message, boolean error)
	{
		statusLabel.setText("<html><body style='width:180px'>" + escapeHtml(message) + "</body></html>");
		statusLabel.setForeground(error ? WdrTheme.ERROR : WdrTheme.TEXT_DIM);
	}

	private static String escapeHtml(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private JPanel labeled(String label, Component field)
	{
		JLabel l = new JLabel(label);
		l.setForeground(WdrTheme.TEXT_DIM);
		l.setFont(FontManager.getRunescapeSmallFont());

		JPanel row = new JPanel(new BorderLayout(0, 1));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		row.add(l, BorderLayout.NORTH);
		row.add(field, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}
}
