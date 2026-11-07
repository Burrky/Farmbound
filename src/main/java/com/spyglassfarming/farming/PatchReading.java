package com.spyglassfarming.farming;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One raw patch value and what the decoder of a given version made of it.
 * Immutable. The raw value is the record; the decoded fields can always be
 * re-derived from it with a newer decoder.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class PatchReading
{
	@Getter
	private final FarmingPatchType patchType;
	@Getter
	private final int raw;
	@Getter
	private final int decoderVersion;
	@Getter
	private final PatchCondition condition;
	/** Null for empty/weeds, for a dead herb (produce unknown) and for UNKNOWN. */
	@Getter
	private final FarmingProduce produce;
	/** Growth stage, harvest lives left, weed level or disease/dead stage, as RuneLite numbers it; -1 when unknown. */
	@Getter
	private final int stage;
	@Getter
	private final PatchSubState subState;

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof PatchReading))
		{
			return false;
		}
		PatchReading r = (PatchReading) o;
		return raw == r.raw && decoderVersion == r.decoderVersion && stage == r.stage && patchType == r.patchType
			&& condition == r.condition && produce == r.produce && subState == r.subState;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(patchType, raw, decoderVersion, condition, produce, stage, subState);
	}

	@Override
	public String toString()
	{
		return patchType + ":" + raw + "=" + condition + (produce == null ? "" : "/" + produce) + "@" + stage
			+ (subState == PatchSubState.NONE ? "" : "/" + subState) + " (v" + decoderVersion + ")";
	}
}
