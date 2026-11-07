package com.spyglassfarming.farming;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Patch types with decoder coverage. Names mirror RuneLite's
 * {@code PatchImplementation}; {@link #isHealthCheckRequired()} is RuneLite's
 * flag of the same name (BSD 2-Clause, see THIRD_PARTY_NOTICES.md).
 *
 * Not covered: compost bins, grape vines and coral (not Spyglass run types),
 * and anything RuneLite does not map.
 */
@AllArgsConstructor
public enum FarmingPatchType
{
	HERB("Herb", false),
	ALLOTMENT("Allotment", false),
	TREE("Tree", true),
	/** Companion patch of the allotment sites; not a run type of its own. */
	FLOWER("Flower", false),
	BUSH("Bush", true),
	FRUIT_TREE("Fruit tree", true),
	HOPS("Hops", false),
	HARDWOOD_TREE("Hardwood tree", true),
	REDWOOD("Redwood", true),
	SPIRIT_TREE("Spirit tree", true),
	ANIMA("Anima", false),
	BELLADONNA("Belladonna", false),
	CACTUS("Cactus", true),
	SEAWEED("Seaweed", false),
	CALQUAT("Calquat", true),
	CELASTRUS("Celastrus", true),
	CRYSTAL_TREE("Crystal tree", true),
	MUSHROOM("Mushroom", false),
	HESPORI("Hespori", true);

	@Getter
	private final String displayName;
	@Getter
	private final boolean healthCheckRequired;
}
