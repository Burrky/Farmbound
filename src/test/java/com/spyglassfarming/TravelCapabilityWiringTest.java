package com.spyglassfarming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.spyglassfarming.account.AccountCapabilityAdapter;
import com.spyglassfarming.account.AccountOwner;
import com.spyglassfarming.account.Known;
import com.spyglassfarming.farming.FarmingCaptureAdapter;
import com.spyglassfarming.farming.FarmingOwner;
import com.spyglassfarming.farming.FarmingSnapshot;
import com.spyglassfarming.travel.Availability;
import com.spyglassfarming.travel.TransportAvailability;
import com.spyglassfarming.account.EvidenceSource;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import org.junit.Test;

/** Plugin glue: Farming evidence crosses only for the same account and profile; the diagnostic is developer-only. */
public class TravelCapabilityWiringTest
{
	private static final Client CLIENT = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p, m, a) ->
	{
		if (m.getName().equals("addChatMessage"))
		{
			throw new AssertionError("no chat outside developer mode");
		}
		Class<?> t = m.getReturnType();
		return t == boolean.class ? false : t == int.class ? 0 : t == long.class ? 0L : null;
	});

	private static FarmingSnapshot available(FarmingOwner owner)
	{
		return new FarmingSnapshot(FarmingSnapshot.State.AVAILABLE, owner, Collections.emptyMap(), Collections.emptyList());
	}

	@Test
	public void farmingEvidence_onlyForTheSameAccountAndProfile()
	{
		AccountOwner a = AccountOwner.standard(5L);
		assertSame(EvidenceSource.NONE, TravelCapabilityWiring.farmingEvidence(a, available(FarmingOwner.standard(6L)), 0L));
		assertSame(EvidenceSource.NONE, TravelCapabilityWiring.farmingEvidence(a, available(new FarmingOwner(5L, "beta")), 0L));
		assertSame(EvidenceSource.NONE, TravelCapabilityWiring.farmingEvidence(null, available(FarmingOwner.standard(5L)), 0L));
		assertSame(EvidenceSource.NONE, TravelCapabilityWiring.farmingEvidence(a, FarmingSnapshot.LOGGED_OUT, 0L));
		EvidenceSource same = TravelCapabilityWiring.farmingEvidence(a, available(FarmingOwner.standard(5L)), 0L);
		assertEquals(Known.UNKNOWN, same.get("farming.spirit_tree.port_sarim"));
		assertTrue(same.explain("farming.spirit_tree.port_sarim").contains("never observed"));
	}

}
