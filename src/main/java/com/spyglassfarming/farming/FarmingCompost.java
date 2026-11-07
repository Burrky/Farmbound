package com.spyglassfarming.farming;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The compost the player plans to use on a patch type. Saved by name (see {@link SavedEnum}); never reorder or rename. */
@RequiredArgsConstructor
public enum FarmingCompost
{
	NONE("No compost"),
	COMPOST("Compost"),
	SUPERCOMPOST("Supercompost"),
	ULTRACOMPOST("Ultracompost"),
	BOTTOMLESS("Bottomless bucket"),
	/** The Lunar spell (83 Magic, Lunar Diplomacy): supercompost, or ultracompost once the ash covered tome is read. */
	FERTILE_SOIL("Fertile Soil");

	@Getter
	private final String label;
}
