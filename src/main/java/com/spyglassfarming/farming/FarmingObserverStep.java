package com.spyglassfarming.farming;

import java.util.Collections;
import java.util.List;
import lombok.Getter;

/** What one tick produced. Immutable. */
public final class FarmingObserverStep
{
	public enum Status
	{
		NOT_LOGGED_IN,
		PROFILE_NOT_READY,
		UNSUPPORTED_PROFILE,
		/**
		 * Classified STANDARD, but on a world type whose persistence is not yet
		 * established ({@link FarmingObserver#SUSPENDED_WORLD_TYPES}). A temporary
		 * integration guard, not a classification fact: nothing is captured.
		 */
		WORLD_TYPE_SUSPENDED,
		WELCOME_SCREEN,
		MODAL_OPEN,
		INSTANCED,
		NO_LOCATION,
		NOT_AT_A_PATCH,
		/** Eligible, but the transmission context has not been stable long enough to read. */
		SETTLING,
		/** Settled: readings taken. */
		CAPTURED
	}

	@Getter
	private final Status status;
	/** The account/profile this step's data belongs to; null when there is none yet. */
	@Getter
	private final FarmingOwner owner;
	/** The owner differs from the previous step's: all earlier in-memory evidence was discarded. */
	@Getter
	private final boolean ownerChanged;
	@Getter
	private final List<PatchObservation> observations;
	/** Patches read on a settled tick whose value had not changed: freshness only, never logged. */
	@Getter
	private final List<String> unchangedPatchIds;
	@Getter
	private final List<CalibrationSample> calibrationSamples;
	@Getter
	private final long observedAt;

	FarmingObserverStep(Status status, FarmingOwner owner, boolean ownerChanged, List<PatchObservation> observations,
		List<String> unchangedPatchIds, List<CalibrationSample> calibrationSamples, long observedAt)
	{
		this.status = status;
		this.owner = owner;
		this.ownerChanged = ownerChanged;
		this.observations = Collections.unmodifiableList(observations);
		this.unchangedPatchIds = Collections.unmodifiableList(unchangedPatchIds);
		this.calibrationSamples = Collections.unmodifiableList(calibrationSamples);
		this.observedAt = observedAt;
	}
}
