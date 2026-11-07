package com.spyglassfarming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * Reads the bundled tab-separated data tables. Lines starting with {@code #} and blank lines are skipped. Every
 * problem (missing file, wrong column count, bad number, unknown name or reference) fails loudly with the file and
 * line. Game ids are written {@code GAMEVAL_NAME=id}; tests check each name against RuneLite's gameval classes.
 */
public final class DataRows
{
	private DataRows()
	{
	}

	public static void read(String file, Consumer<Row> handler)
	{
		try (InputStream in = DataRows.class.getResourceAsStream(file))
		{
			if (in == null)
			{
				throw new IllegalStateException("Missing " + file);
			}
			parse(file, new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), handler);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unreadable " + file, e);
		}
	}

	public static void parse(String file, BufferedReader reader, Consumer<Row> handler) throws IOException
	{
		String line;
		int n = 0;
		while ((line = reader.readLine()) != null)
		{
			n++;
			if (line.trim().isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			try
			{
				handler.accept(new Row(line.split("\t", -1)));
			}
			catch (RuntimeException e)
			{
				throw new IllegalStateException(file + " line " + n + ": " + e.getMessage(), e);
			}
		}
	}

	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Row
	{
		private final String[] f;

		public String op()
		{
			return f[0];
		}

		public int size()
		{
			return f.length;
		}

		public Row cols(int min, int max)
		{
			if (f.length < min || f.length > max)
			{
				throw new IllegalArgumentException((min == max ? "" + min : min + "-" + max) + " columns expected, found " + f.length);
			}
			return this;
		}

		/** A required, non-empty text column; {@code -} stands for the empty string. */
		public String s(int i)
		{
			String v = col(i);
			if (v.isEmpty())
			{
				throw new IllegalArgumentException("column " + (i + 1) + " is empty");
			}
			return v.equals("-") ? "" : v;
		}

		public int i(int i)
		{
			return Integer.parseInt(col(i));
		}

		public boolean flag(int i)
		{
			switch (col(i))
			{
				case "1":
					return true;
				case "0":
					return false;
				default:
					throw new IllegalArgumentException("column " + (i + 1) + ": 0 or 1 expected, found " + col(i));
			}
		}

		/** The id of a {@code GAMEVAL_NAME=id} column. */
		public int id(int i)
		{
			String v = col(i);
			int eq = v.indexOf('=');
			if (eq <= 0)
			{
				throw new IllegalArgumentException("column " + (i + 1) + ": NAME=id expected, found " + v);
			}
			return Integer.parseInt(v.substring(eq + 1));
		}

		/** The gameval name of a {@code GAMEVAL_NAME=id} column. */
		public String gameval(int i)
		{
			id(i);
			String v = col(i);
			return v.substring(v.lastIndexOf('.', v.indexOf('=')) + 1, v.indexOf('='));
		}

		/** The ids of every column from {@code from} on. */
		public int[] ids(int from)
		{
			int[] out = new int[Math.max(0, f.length - from)];
			for (int k = 0; k < out.length; k++)
			{
				out[k] = id(from + k);
			}
			return out;
		}

		public String[] rest(int from)
		{
			String[] out = new String[Math.max(0, f.length - from)];
			for (int k = 0; k < out.length; k++)
			{
				out[k] = s(from + k);
			}
			return out;
		}

		public <E extends Enum<E>> E e(Class<E> type, int i)
		{
			return Enum.valueOf(type, col(i));
		}

		public IllegalArgumentException unknownOp()
		{
			return new IllegalArgumentException("unknown row type " + f[0]);
		}

		private String col(int i)
		{
			if (i >= f.length)
			{
				throw new IllegalArgumentException(f[0] + ": column " + (i + 1) + " missing");
			}
			return f[i];
		}
	}
}
