package com.spyglassfarming.travel;

import org.junit.Test;

/** Runs the build tools when asked (GENERATE=catalog): {@code gradlew test --tests '*GenerateData*'}. */
public class GenerateData
{
	@Test
	public void generate() throws Exception
	{
		if ("transitions".equals(System.getenv("GENERATE")))
		{
			com.spyglassfarming.navigation.TransitionsBuilder.main(new String[]{"src/main/resources/com/spyglassfarming/navigation/transitions.json"});
		}
		if ("catalog".equals(System.getenv("GENERATE")))
		{
			CatalogBuilder.main(new String[]{"src/main/resources/com/spyglassfarming/travel/catalog.json"});
		}
	}
}
