package com.spyglassfarming.farming;

import static com.spyglassfarming.farming.FarmingFixtures.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.spyglassfarming.storage.LocalStateStore;
import com.spyglassfarming.storage.TestFilepaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Owner-scoped persistence: stale loads, write ownership, load-before-write, decoder versions and freshness batching. */
public class FarmingStoreTest
{
	private static final Gson GSON = new Gson();
	private static final FarmingOwner A_OWNER = FarmingOwner.standard(ACCOUNT);
	private static final FarmingOwner B_OWNER = FarmingOwner.standard(OTHER_ACCOUNT);

	private Path root;
	private Filepath rootPath;
	private LocalStateStore stateStore;
	private final Deque<Runnable> loads = new ArrayDeque<>();
	private FarmingStore store;
	private Function<FarmingOwner, Filepath> dirs;

	@Before
	public void setUp() throws Exception
	{
		root = Files.createTempDirectory("farming-store");
		rootPath = TestFilepaths.rooted(root);
		stateStore = new LocalStateStore(GSON);
		stateStore.start();
		dirs = owner ->
		{
			Filepath dir = rootPath.joinSegment(Long.toString(owner.getAccountHash())).joinSegment("profiles").joinSegment(owner.getProfileKey()).joinSegment("farming");
			try
			{
				dir.createDirectories();
			}
			catch (Exception e)
			{
				throw new IllegalStateException(e);
			}
			return dir;
		};
		store = new FarmingStore(stateStore, dirs, loads::add, FarmingPatchCatalog.standard());
	}

	@After
	public void tearDown() throws Exception
	{
		stateStore.shutdown();
		rootPath.deleteRecursively();
	}

	private void drainWrites()
	{
		// A blocking write queues behind every earlier async write: a barrier.
		stateStore.writeAndWait(rootPath.joinSegment("barrier.json"), Collections.singletonMap("b", 1), 10_000);
	}

	private FarmingDocuments.Patches readPatches(FarmingOwner owner)
	{
		return stateStore.readIfExists(dirs.apply(owner).joinSegment(FarmingDocuments.PATCHES_FILE), FarmingDocuments.Patches.class);
	}

	private static FarmingObserverStep step(FarmingOwner owner, long at, PatchObservation... observations)
	{
		return new FarmingObserverStep(FarmingObserverStep.Status.CAPTURED, owner, false, java.util.Arrays.asList(observations),
			Collections.emptyList(), Collections.emptyList(), at);
	}

	private static FarmingObserverStep unchanged(FarmingOwner owner, long at, String patchId)
	{
		return new FarmingObserverStep(FarmingObserverStep.Status.CAPTURED, owner, false, Collections.emptyList(),
			Collections.singletonList(patchId), Collections.emptyList(), at);
	}

	private static PatchObservation baseline(String patchId, int raw, long at)
	{
		FarmingPatchDef p = patch(patchId);
		return new PatchObservation(PatchObservation.Kind.BASELINE, p, FarmingDecoder.decode(p.getType(), raw), null, at, 0L, 1);
	}

	private void seedFile(FarmingOwner owner, String patchId, int raw, int decoderVersion)
	{
		FarmingPatchDef p = patch(patchId);
		Map<String, PatchKnowledge> m = new HashMap<>();
		PatchKnowledge k = CropCycleAttribution.apply(null, baseline(patchId, raw, T0)).withDecoder(decoderVersion,
			CropCycleAttribution.apply(null, baseline(patchId, raw, T0)).getCycle(), false);
		m.put(p.getPatchId(), k);
		stateStore.writeAndWait(dirs.apply(owner).joinSegment(FarmingDocuments.PATCHES_FILE), FarmingDocuments.Patches.of(owner, m), 10_000);
	}

	@Test
	public void delayedLoadForAccountA_neverReplacesAccountB()
	{
		seedFile(A_OWNER, "herb.falador", 36, FarmingDecoder.VERSION);
		seedFile(B_OWNER, "herb.catherby", 34, FarmingDecoder.VERSION);
		store.switchOwner(A_OWNER, T0);
		Runnable loadA = loads.poll();
		store.switchOwner(B_OWNER, T0);
		loads.poll().run(); // B's load
		loadA.run(); // A's load arrives late
		assertEquals(B_OWNER, store.getOwner());
		assertTrue(store.snapshot().containsKey("herb.catherby"));
		assertFalse("A's data never lands in B's state", store.snapshot().containsKey("herb.falador"));
	}

	@Test
	public void fileNamingAnotherOwner_isNotLoaded()
	{
		seedFile(A_OWNER, "herb.falador", 36, FarmingDecoder.VERSION);
		store.switchOwner(B_OWNER, T0);
		// Hand B's load A's document, as if a file had been copied between accounts.
		store.deliver(1, B_OWNER, readPatches(A_OWNER), null);
		assertTrue(store.snapshot().isEmpty());
	}

	@Test
	public void queuedWrite_keepsItsOwnerAndSnapshot_acrossAnAccountSwitch()
	{
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		store.apply(step(A_OWNER, T0, baseline("herb.falador", 33, T0)));
		store.switchOwner(B_OWNER, T0 + 1000);
		loads.poll().run();
		drainWrites();
		FarmingDocuments.Patches a = readPatches(A_OWNER);
		assertEquals(ACCOUNT, a.accountHash);
		assertEquals(33, a.patches.get("herb.falador").getLastRaw());
		assertNull("nothing was written for B", readPatches(B_OWNER));
	}

	@Test
	public void stepForAnotherOwner_isIgnored()
	{
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		store.apply(step(B_OWNER, T0, baseline("herb.falador", 33, T0)));
		assertTrue(store.snapshot().isEmpty());
	}

	@Test
	public void nothingIsWrittenBeforeTheOwnersFileHasLoaded_thenBothAreKept()
	{
		seedFile(A_OWNER, "tree.taverley", 9, FarmingDecoder.VERSION);
		store.switchOwner(A_OWNER, T0);
		store.apply(step(A_OWNER, T0, baseline("herb.falador", 33, T0)));
		drainWrites();
		assertEquals("the stored file is untouched", Collections.singleton("tree.taverley"), readPatches(A_OWNER).patches.keySet());
		loads.poll().run();
		store.flush(T0 + 1);
		drainWrites();
		assertEquals(2, readPatches(A_OWNER).patches.size());
	}

	@Test
	public void roundTrip_andOlderDecoderVersion_isRedecoded()
	{
		seedFile(A_OWNER, "herb.falador", 33, 0);
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		PatchKnowledge k = store.snapshot().get("herb.falador");
		assertEquals(FarmingDecoder.VERSION, k.getDecoderVersion());
		assertFalse(k.isDecodeStale());
		assertEquals(FarmingProduce.RANARR, k.getCycle().getProduce());
		store.flush(T0);
		drainWrites();
		assertEquals(FarmingDecoder.VERSION, readPatches(A_OWNER).patches.get("herb.falador").getDecoderVersion());
	}

	@Test
	public void storedValueTheCurrentDecoderCannotRead_isMarkedStale()
	{
		seedFile(A_OWNER, "herb.falador", 220, 0);
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		assertTrue(store.snapshot().get("herb.falador").isDecodeStale());
	}

	@Test
	public void compost_isSavedPerOwner_andReloaded_neverAnotherAccounts()
	{
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		PatchCompost.Message m = PatchCompost.parse("You treat the herb patch with supercompost.");
		store.recordCompost(A_OWNER, "herb.falador", PatchCompost.of(m, T0 + 1, null, patch("herb.falador")), T0 + 1);
		store.recordCompost(B_OWNER, "herb.catherby", PatchCompost.of(m, T0 + 1, null, patch("herb.catherby")), T0 + 1); // not the owner: ignored
		assertEquals(PatchCompost.Kind.SUPERCOMPOST, store.snapshotFor(A_OWNER).compostFor("herb.falador").getKind());
		store.flush(T0 + 2);
		drainWrites();
		store.switchOwner(B_OWNER, T0 + 3);
		loads.poll().run();
		assertTrue("another account sees none of it", store.snapshotFor(B_OWNER).getCompost().isEmpty());
		store.switchOwner(A_OWNER, T0 + 4);
		loads.poll().run();
		assertEquals("reloaded after a logout or hop", PatchCompost.Kind.SUPERCOMPOST, store.snapshotFor(A_OWNER).compostFor("herb.falador").getKind());
	}

	@Test
	public void bins_areSavedPerOwner_reconfirmedRarely_andUnknownValuesDropped()
	{
		store.switchOwner(A_OWNER, T0);
		loads.poll().run();
		store.recordBin(A_OWNER, "bin.falador", 95, T0 + 1); // supercompost rotting
		store.recordBin(A_OWNER, "bin.falador", 95, T0 + 2); // unchanged: not rewritten
		assertEquals(T0 + 1, store.snapshotFor(A_OWNER).getBins().get("bin.falador").getAt());
		store.recordBin(A_OWNER, "bin.falador", 95, T0 + 1 + FarmingStore.BIN_CONFIRM_MS);
		CompostBin.Seen s = store.snapshotFor(A_OWNER).getBins().get("bin.falador");
		assertEquals("re-confirmed", T0 + 1 + FarmingStore.BIN_CONFIRM_MS, s.getAt());
		assertEquals("the same value since it was first seen", T0 + 1, s.getSince());
		store.recordBin(A_OWNER, "bin.catherby", 250, T0 + 3);
		assertNull("not a value the table knows", store.snapshotFor(A_OWNER).getBins().get("bin.catherby"));
		store.recordBin(B_OWNER, "bin.ardougne", 62, T0 + 3);
		assertNull("another account's reading is never kept here", store.snapshotFor(A_OWNER).getBins().get("bin.ardougne"));
		store.flush(T0 + 4 + FarmingStore.BIN_CONFIRM_MS);
		drainWrites();
		store.switchOwner(B_OWNER, T0 + 5 + FarmingStore.BIN_CONFIRM_MS);
		loads.poll().run();
		assertTrue(store.snapshotFor(B_OWNER).getBins().isEmpty());
		store.switchOwner(A_OWNER, T0 + 6 + FarmingStore.BIN_CONFIRM_MS);
		loads.poll().run();
		assertEquals("kept across logout, hops and runs", 95, store.snapshotFor(A_OWNER).getBins().get("bin.falador").getRaw());
	}
}
