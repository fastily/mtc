package mtc;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

import fastily.jwiki.core.ColorLog;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Miscellaneous utility functions for MTC.
 * 
 * @author Fastily
 *
 */
public class Utils
{
	/**
	 * Random number generator.
	 * 
	 * @see #permuteFileName(String)
	 */
	private static final Random rand = new Random();

	/**
	 * Constructors disallowed
	 */
	private Utils()
	{

	}

	/**
	 * Permutes a filename by adding a random number to the end before the file extension. PRECONDITION: {@code fn} is a
	 * valid filename with an extension, of the format (e.g. blahblah.jpg)
	 * 
	 * @param fn The base filename to permute
	 * @return The permuted filename
	 */
	public static String permuteFileName(String fn)
	{
		return new StringBuffer(fn).insert(fn.lastIndexOf('.'), " " + rand.nextInt()).toString();
	}

	/**
	 * Downloads a file and saves it to disk.
	 * 
	 * @param client The OkHttpClient to use perform network connections with.
	 * @param u The url to download from
	 * @param localpath The local path to save the file at.
	 * @return True on success.
	 */
	public static boolean downloadFile(OkHttpClient client, String u, String localpath)
	{
		ColorLog.fyi("Downloading a file to " + localpath);

		byte[] bf = new byte[1024 * 512]; // 512kb buffer.
		int read;
		try (Response r = client.newCall(new Request.Builder().url(u).get().build()).execute();
				OutputStream out = Files.newOutputStream(Paths.get(localpath)))
		{
			InputStream in = r.body().byteStream();
			while ((read = in.read(bf)) > -1)
				out.write(bf, 0, read);

			return true;
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}

		return false;
	}
}