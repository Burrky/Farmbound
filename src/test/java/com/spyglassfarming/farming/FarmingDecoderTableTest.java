package com.spyglassfarming.farming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.Test;

/** The patch-state rows as a resource (farming/patch-states.tsv): every decoding unchanged, and the file validated. */
public class FarmingDecoderTableTest
{
	/**
	 * SHA-256 of every decoding (type, raw 0..255 → condition, produce, stage, sub-state), taken from the rows as they were
	 * written in Java before they moved to the resource (2026-11-04). Any change to a decoded meaning changes it.
	 */
	private static final String DECODINGS = "2fd18de5fac7a2a078b95f30029a1af89465a40b33987639200df512a18cd9f6";

	@Test
	public void everyDecoding_isUnchanged() throws Exception
	{
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		for (FarmingPatchType t : FarmingPatchType.values())
		{
			for (int raw = 0; raw <= 255; raw++)
			{
				PatchReading r = FarmingDecoder.decode(t, raw);
				md.update((t + ":" + raw + "=" + r.getCondition() + "," + r.getProduce() + "," + r.getStage() + "," + r.getSubState() + ";")
					.getBytes(StandardCharsets.UTF_8));
			}
		}
		StringBuilder b = new StringBuilder();
		for (byte x : md.digest())
		{
			b.append(String.format("%02x", x));
		}
		assertEquals(DECODINGS, b.toString());
	}

	@Test
	public void theResource_hasEveryPatchType_andEveryRow() throws Exception
	{
		int rows = 0;
		for (FarmingPatchType t : FarmingPatchType.values())
		{
			assertTrue(t.name(), FarmingDecoderTable.rows(t).length > 0);
			rows += FarmingDecoderTable.rows(t).length;
		}
		assertEquals("every row of the original table", 503, rows);
	}

	@Test
	public void aMalformedFile_failsLoudly_withItsLine()
	{
		String ok = "HERB\t0\t3\tWEEDS\t-\tREVERSE\t3\tNONE\n";
		expectFailure("HERB\t0\t3\tWEEDS\t-\tREVERSE\t3\n", "line 1: 8 columns");
		expectFailure("HERB\t4\t3\tWEEDS\t-\tREVERSE\t3\tNONE\n", "bad range");
		expectFailure("HERB\t0\t3\tWILTED\t-\tREVERSE\t3\tNONE\n", "line 1");
		expectFailure("HERB\t0\t3\tWEEDS\t-\tSIDEWAYS\t3\tNONE\n", "unknown stage mode");
		expectFailure("HERB\t0\t3\tGROWING\t-\tLAST\t0\tNONE\n", "LAST needs a produce");
		expectFailure("# only a comment\n" + ok, "no rows for ALLOTMENT");
	}

	private static void expectFailure(String text, String message)
	{
		try
		{
			FarmingDecoderTable.parse(new BufferedReader(new StringReader(text)));
			fail("accepted: " + text);
		}
		catch (Exception e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains(message));
		}
	}
}
