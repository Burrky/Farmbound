package com.spyglassfarming.farming;

import com.google.gson.Gson;
import com.spyglassfarming.storage.LocalStateStore;
import com.spyglassfarming.storage.TestFilepaths;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.Scene;
import net.runelite.api.Point;
import net.runelite.api.ObjectComposition;
import net.runelite.api.GameObject;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.CollisionData;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.util.Filepath;

/**
 * A real {@link FarmingCaptureAdapter} (real observer, stores and run machinery, files in a temp folder) against a fake
 * client that only answers what the adapter asks: logged in on a members world, a position and patch varbit values.
 * Synthetic data only.
 */
public final class RunHarness implements AutoCloseable
{
	public static final Gson GSON = new Gson();
	public static final WorldPoint HERB_SITE = new WorldPoint(3055, 3307, 0);
	public static final WorldPoint TREE_SITE = new WorldPoint(3010, 3330, 0);
	public static final long ACCOUNT = FarmingFixtures.ACCOUNT;
	public static final int A = FarmingFixtures.A;
	public static final int B = FarmingFixtures.B;
	public static final int C = FarmingFixtures.C;
	public static final int D = FarmingFixtures.D;

	/** The client state behind a {@link Client} proxy. */
	public static final class Fake
	{
		public int tick = 100;
		public WorldPoint location = HERB_SITE;
		public final Map<Integer, Integer> varbits = new HashMap<>();
		public final Map<Integer, Integer> varps = new HashMap<>();
		/** Patch objects in the world: object id, varbit, x1, y1, x2, y2, plane (the scene shows those it has loaded). */
		public final java.util.List<int[]> patchObjects = new java.util.ArrayList<>();
		/** Tiles (x, y, plane) that block movement in the loaded scene. */
		public final java.util.List<int[]> blocked = new java.util.ArrayList<>();
		private WorldView worldView()
		{
			return worldViewFor(this);
		}

		private ObjectComposition definition(int id)
		{
			for (int[] o : patchObjects)
			{
				if (o[0] == id)
				{
					return proxy(ObjectComposition.class, (op, om, oa) -> om.getName().equals("getVarbitId") ? o[1]
						: om.getName().equals("getName") ? "Patch" : dflt(om.getReturnType()));
				}
			}
			return null;
		}

		public final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getGameState":
					return GameState.LOGGED_IN;
				case "getTickCount":
					return tick;
				case "getAccountHash":
					return ACCOUNT;
				case "getWorld":
					return 330;
				case "getWorldType":
					return EnumSet.of(WorldType.MEMBERS);
				case "getComponentTable":
					return proxy(HashTable.class, (hp, hm, ha) -> hm.getName().equals("iterator") ? Collections.emptyList().iterator() : dflt(hm.getReturnType()));
				case "getTopLevelWorldView":
					return worldView();
				case "getLocalPlayer":
					return proxy(Player.class, (pp, pm, pa) -> pm.getName().equals("getWorldLocation") ? location : dflt(pm.getReturnType()));
				case "getWidget":
					return null;
				case "getObjectDefinition":
					return definition((Integer) a[0]);
				case "isInInstancedRegion":
					return false;
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) a[0], 0);
				case "getVarpValue":
					return varps.getOrDefault((Integer) a[0], 0);
				default:
					return dflt(m.getReturnType());
			}
		});
	}

	/**
	 * The loaded scene around the player: 104 x 104 tiles based on the player's 8-tile zone (as the client loads it), every
	 * tile walkable unless {@link Fake#blocked} says otherwise, no objects. Its base follows the player (a teleport loads a new
	 * scene).
	 */
	static WorldView worldViewFor(Fake f)
	{
		WorldPoint at = f.location;
		int baseX = ((at.getX() >> 3) - 6) << 3;
		int baseY = ((at.getY() >> 3) - 6) << 3;
		CollisionData[] maps = new CollisionData[4];
		for (int z = 0; z < 4; z++)
		{
			int[][] flags = new int[104][104];
			for (int[] b : f.blocked)
			{
				if (b[2] == z && b[0] - baseX >= 0 && b[0] - baseX < 104 && b[1] - baseY >= 0 && b[1] - baseY < 104)
				{
					flags[b[0] - baseX][b[1] - baseY] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				}
			}
			maps[z] = proxy(CollisionData.class, (cp, cm, ca) -> cm.getName().equals("getFlags") ? flags : dflt(cm.getReturnType()));
		}
		Tile[][][] tiles = new Tile[4][104][104];
		for (int[] o : f.patchObjects)
		{
			int sx = o[2] - baseX;
			int sy = o[3] - baseY;
			if (sx < 0 || sy < 0 || sx >= 104 || sy >= 104)
			{
				continue;
			}
			Point min = new Point(sx, sy);
			Point max = new Point(o[4] - baseX, o[5] - baseY);
			WorldPoint world = new WorldPoint(o[2], o[3], o[6]);
			GameObject g = proxy(GameObject.class, (gp, gm, ga) ->
			{
				switch (gm.getName())
				{
					case "getId":
						return o[0];
					case "getSceneMinLocation":
						return min;
					case "getSceneMaxLocation":
						return max;
					case "getWorldLocation":
						return world;
					case "getPlane":
						return o[6];
					case "hashCode":
						return System.identityHashCode(gp);
					case "equals":
						return gp == ga[0];
					default:
						return dflt(gm.getReturnType());
				}
			});
			tiles[o[6]][sx][sy] = proxy(Tile.class, (tp, tm, ta) -> tm.getName().equals("getGameObjects") ? new GameObject[]{g}
				: tm.getName().equals("getSceneLocation") ? min : dflt(tm.getReturnType()));
		}
		Scene scene = proxy(Scene.class, (sp, sm, sa) -> sm.getName().equals("getTiles") ? tiles : dflt(sm.getReturnType()));
		return proxy(WorldView.class, (vp, vm, va) ->
		{
			switch (vm.getName())
			{
				case "isInstance":
					return false;
				case "getCollisionMaps":
					return maps;
				case "getBaseX":
					return baseX;
				case "getBaseY":
					return baseY;
				case "getPlane":
					return at.getPlane();
				case "getScene":
					return scene;
				case "npcs":
				case "players":
					return emptySet(vm.getReturnType());
				default:
					return dflt(vm.getReturnType());
			}
		});
	}

	/** An empty iterable of the API's own set type (no NPCs or players in the scene). */
	private static Object emptySet(Class<?> type)
	{
		if (!type.isInterface())
		{
			return null;
		}
		return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> m.getName().equals("iterator")
			? Collections.emptyIterator() : m.getName().equals("stream") ? java.util.stream.Stream.empty() : dflt(m.getReturnType()));
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler h)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
	}

	private static Object dflt(Class<?> t)
	{
		if (t == boolean.class)
		{
			return false;
		}
		if (t == int.class || t == short.class || t == byte.class)
		{
			return 0;
		}
		if (t == char.class)
		{
			return (char) 0;
		}
		if (t == long.class)
		{
			return 0L;
		}
		if (t == float.class)
		{
			return 0f;
		}
		if (t == double.class)
		{
			return 0d;
		}
		return null;
	}

	public final FarmingPatchCatalog catalog = FarmingPatchCatalog.standard();
	public final Fake fake = new Fake();
	private final Path root;
	private final Filepath rootPath;
	private final LocalStateStore store;
	private final FarmingCaptureAdapter adapter;

	public RunHarness() throws Exception
	{
		root = Files.createTempDirectory("standalone-run");
		rootPath = TestFilepaths.rooted(root);
		store = new LocalStateStore(GSON);
		store.start();
		adapter = new FarmingCaptureAdapter(fake.client, GSON, store);
		adapter.dirs = owner ->
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
		adapter.clock = () -> FarmingFixtures.T0 + fake.tick * 600L;
	}

	/** The adapter's clock (game ticks from a fixed start). */
	public long now()
	{
		return adapter.clock.getAsLong();
	}

	public FarmingCaptureAdapter adapter()
	{
		return adapter;
	}

	public LocalStateStore store()
	{
		return store;
	}

	public Filepath rootDir()
	{
		return rootPath;
	}

	public Filepath accountDir()
	{
		return rootPath.joinSegment(Long.toString(ACCOUNT));
	}

	/** Starts the adapter and waits for the owner's (empty) patch and run files to load. */
	public void start() throws InterruptedException
	{
		adapter.start();
		settle(6);
		for (int i = 0; i < 500 && !(adapter.live().store.isLoaded() && adapter.live().runs.isLoaded()); i++)
		{
			Thread.sleep(10);
		}
	}

	public void settle(int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			fake.tick++;
			adapter.onGameTick(new GameTick());
		}
	}

	/** The player stands at {@code where} and sees these patch values for a while (a patch is read once the player has settled). */
	public void see(WorldPoint where, Map<Integer, Integer> varbits)
	{
		fake.location = where;
		fake.varbits.putAll(varbits);
		settle(6);
	}

	/** The game's own "You treat the ... patch with ..." message after the player used compost on the patch. */
	public boolean compost(String patchId, String message)
	{
		FarmingSites.Tile t = FarmingSites.tileOf(catalog, patchId);
		adapter.pendingCompost.add(new FarmingCaptureAdapter.PendingCompost(patchId, t.x, t.y, t.x, t.y, t.plane, adapter.clock.getAsLong() + 60_000));
		return adapter.onCompostMessage(message, new WorldPoint(t.x, t.y, t.plane));
	}

	@Override
	public void close() throws Exception
	{
		adapter.shutDown();
		store.shutdown();
		rootPath.deleteRecursively();
	}
}
