package com.spyglassfarming.farming;

import lombok.Getter;

/**
 * Evidence of an exact farm growth tick: a same-sequence, tick-to-tick change
 * that only the server's growth tick can cause (RuneLite FarmingTracker's
 * isObservedGrowthTick rules, adapted). The growth tick happened in
 * ({@link #getPreviousObservedAt()}, {@link #getObservedAt()}]. Projection
 * and offset maths are later work; this only preserves the evidence.
 */
public final class CalibrationSample
{
	@Getter
	private final String patchId;
	@Getter
	private final FarmingProduce produce;
	/** The growth-tick length that applied to the previous state (RuneLite: previous state's tick rate). */
	@Getter
	private final int tickMinutes;
	@Getter
	private final int fromRaw;
	@Getter
	private final int toRaw;
	@Getter
	private final PatchCondition fromCondition;
	@Getter
	private final PatchCondition toCondition;
	@Getter
	private final int fromStage;
	@Getter
	private final int toStage;
	@Getter
	private final long previousObservedAt;
	@Getter
	private final long observedAt;
	@Getter
	private final long sequence;
	@Getter
	private final int decoderVersion;

	CalibrationSample(String patchId, FarmingProduce produce, int tickMinutes, PatchReading from, PatchReading to, long previousObservedAt, long observedAt, long sequence)
	{
		this.patchId = patchId;
		this.produce = produce;
		this.tickMinutes = tickMinutes;
		this.fromRaw = from.getRaw();
		this.toRaw = to.getRaw();
		this.fromCondition = from.getCondition();
		this.toCondition = to.getCondition();
		this.fromStage = from.getStage();
		this.toStage = to.getStage();
		this.previousObservedAt = previousObservedAt;
		this.observedAt = observedAt;
		this.sequence = sequence;
		this.decoderVersion = to.getDecoderVersion();
	}

	/**
	 * Whether a same-sequence tick-to-tick change is a growth tick. Adapted from
	 * RuneLite's FarmingTracker.isObservedGrowthTick: weeds and produce changes
	 * never count; only a GROWING previous state has a growth tick rate (so
	 * diseased-to-dead, a 0-rate state in RuneLite, is excluded); and growing
	 * to harvestable counts only for produce without a health check. Spyglass's
	 * separate CHECK_HEALTH condition is RuneLite's "growing at the last stage",
	 * so growing to check-health at stage + 1 counts, and check-health to
	 * harvestable (a player action) does not.
	 */
	static boolean isGrowthTick(PatchReading from, PatchReading to)
	{
		FarmingProduce produce = from.getProduce();
		if (produce == null || produce == FarmingProduce.WEEDS || to.getProduce() != produce || produce.getTickMinutes() <= 0)
		{
			return false;
		}
		if (from.getCondition() != PatchCondition.GROWING)
		{
			return false;
		}
		switch (to.getCondition())
		{
			case GROWING:
			case CHECK_HEALTH:
				return to.getStage() - from.getStage() == 1;
			case DISEASED:
				return true;
			case HARVESTABLE:
				return !produce.requiresHealthCheck();
			default:
				return false;
		}
	}
}
