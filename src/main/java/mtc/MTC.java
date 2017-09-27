package mtc;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import fastily.jwiki.core.ColorLog;
import fastily.jwiki.core.MQuery;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.dwrap.ImageInfo;
import fastily.jwiki.tp.WParser;
import fastily.jwiki.tp.WTemplate;
import fastily.jwiki.tp.WikiText;
import fastily.jwiki.util.FL;
import fastily.jwiki.util.FSystem;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Business Logic for MTC. Contains shared methods, constants, and Objects.
 * 
 * @author Fastily
 *
 */
public final class MTC
{
	/**
	 * Cache of whether a Template exists on Commons.
	 */
	protected static HashMap<String, Boolean> ctpCache = new HashMap<>();

	/**
	 * Path pointing to temporary folder to store downloaded files.
	 */
	protected static Path mtcfiles = Paths.get((System.getProperty("os.name").contains("Windows") ? "" : "/tmp/") + "mtcfiles");

	/**
	 * The Wiki objects to use
	 */
	protected Wiki enwp = new Wiki("en.wikipedia.org"), com = new Wiki("commons.wikimedia.org");

	/**
	 * Regex matching Copy to Commons templates.
	 */
	protected String mtcRegex;

	/**
	 * Flag indicating whether this is a debug-mode/dry run (do not perform transfers)
	 */
	protected boolean dryRun = false;

	/**
	 * Flag indicating whether the non-free content filter is to be ignored.
	 */
	protected boolean ignoreFilter = false;

	/**
	 * Flag indicating whether the Commons category tracking transfers should be used.
	 */
	protected boolean useTrackingCat = true;

	/**
	 * Flag indicating whether we should attempt deletion on successful transfer.
	 */
	protected boolean deleteOnTransfer = false;

	/**
	 * Contains redirect data for license tags
	 */
	protected HashMap<String, String> tpMap = new HashMap<>();

	/**
	 * Files with these categories should not be transferred.
	 */
	protected HashSet<String> blacklist;

	/**
	 * Files must be members of at least one of the following categories to be eligible for transfer.
	 */
	protected HashSet<String> whitelist;

	/**
	 * Generic http client for downloading files
	 */
	private OkHttpClient httpClient = new OkHttpClient();

	/**
	 * Initializes the Wiki objects and download folders for MTC.
	 */
	public MTC()
	{
		this(false);
	}

	/**
	 * Creates an MTC object.
	 * 
	 * @param cliOnly Set true to disable download folder creation. CAVEAT: This is only for read-only usage.
	 */
	public MTC(boolean cliOnly)
	{
		// Generate whitelist & blacklist
		HashMap<String, ArrayList<String>> l = MQuery.getLinksOnPage(enwp,
				FL.toSAL(MStrings.fullname + "/Blacklist", MStrings.fullname + "/Whitelist"));
		blacklist = new HashSet<>(l.get(MStrings.fullname + "/Blacklist"));
		whitelist = new HashSet<>(l.get(MStrings.fullname + "/Whitelist"));

		// Generate download directory
		try
		{
			if (!cliOnly && !Files.isDirectory(mtcfiles))
				Files.createDirectory(mtcfiles);
		}
		catch (Throwable e)
		{
			FSystem.errAndExit(e, "Failed to create output folder.  Do you have write permissions?");
		}

		// Process template redirect data
		for (String line : enwp.getPageText(MStrings.fullname + "/Redirects").split("\n"))
			if (!line.startsWith("<") && !line.isEmpty())
			{
				String[] splits = line.split("\\|");
				for (String s : splits)
					tpMap.put(s, splits[0]);
			}

		// Setup mtcRegex
		ArrayList<String> rtl = enwp.nss(enwp.whatLinksHere("Template:Copy to Wikimedia Commons", true));
		rtl.add("Copy to Wikimedia Commons");
		mtcRegex = "(?si)\\{\\{(" + FL.pipeFence(rtl).replace(" ", "( |_)") + ")\\}\\}\\n?";
	}

	/**
	 * Attempts login as the specified user.
	 * 
	 * @param user The username to login as
	 * @param px The password to login with
	 * @return True on success.
	 */
	public boolean login(String user, String px)
	{
		try
		{
			return enwp.login(user, px) && com.login(user, px);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Filters (if enabled) and resolves Commons filenames for transfer candidates
	 * 
	 * @param titles The local files to transfer
	 * @return An ArrayList of TransferObject objects.
	 */
	public ArrayList<TransferFile> filterAndResolve(ArrayList<String> titles)
	{
		MQuery.getSharedDuplicatesOf(enwp, titles).forEach((k, v) -> {
			if (!v.isEmpty())
				titles.remove(k);
		});

		HashMap<String, ArrayList<String>> catL = MQuery.getCategoriesOnPage(enwp, titles);

		if (!ignoreFilter)
			catL.forEach((k, v) -> {
				if (v.stream().anyMatch(blacklist::contains) || !v.stream().anyMatch(whitelist::contains))
					titles.remove(k);
			});

		ArrayList<TransferFile> l = new ArrayList<>();
		resolveFileNames(titles).forEach((k, v) -> l.add(new TransferFile(k, v, catL.get(k).contains("Category:Self-published work"))));

		return l;
	}

	/**
	 * Find available file names on Commons for each enwp file. The enwp filename will be returned if it is free on
	 * Commons, otherwise it will be permuted.
	 * 
	 * @param l The list of enwp files to find a Commons filename for
	 * @return The Map such that [ enwp_filename : commons_filename ]
	 */
	private HashMap<String, String> resolveFileNames(ArrayList<String> l)
	{
		HashMap<String, String> m = new HashMap<>();
		MQuery.exists(com, l).forEach((k, v) -> {
			if (!v)
				m.put(k, k);
			else
			{
				String comFN;
				do
				{
					comFN = new StringBuilder(k).insert(k.lastIndexOf('.'), " " + Math.round(Math.random() * 1000)).toString();
				} while (com.exists(comFN)); // loop until available filename is found

				m.put(k, comFN);
			}
		});

		return m;
	}

	/**
	 * Downloads a file and saves it to disk.
	 * 
	 * @param client The OkHttpClient to use perform network connections with.
	 * @param u The url to download from
	 * @param localpath The local path to save the file at.
	 * @return True on success.
	 */
	private static boolean downloadFile(OkHttpClient client, String u, Path localpath)
	{
		ColorLog.fyi("Downloading a file to " + localpath);

		byte[] bf = new byte[1024 * 512]; // 512kb buffer.
		int read;
		try (Response r = client.newCall(new Request.Builder().url(u).get().build()).execute();
				OutputStream out = Files.newOutputStream(localpath))
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

	/**
	 * Represents a file to transfer to Commons
	 * 
	 * @author Fastily
	 *
	 */
	public class TransferFile
	{
		/**
		 * The enwp filename
		 */
		protected String wpFN;

		/**
		 * The commons filename
		 */
		private String comFN;

		/**
		 * The local path
		 */
		private Path localFN;

		/**
		 * The summary and license sections.
		 */
		private StringBuilder sumSection = new StringBuilder("== {{int:filedesc}} ==\n"),
				licSection = new StringBuilder("\n== {{int:license-header}} ==\n");

		/**
		 * The list of old revisions for the file
		 */
		private ArrayList<ImageInfo> imgInfoL;

		/**
		 * The user who originally uploaded the file. Excludes {@code User:} prefix.
		 */
		private String uploader;

		/**
		 * Flag indicating if this file is tagged as own work.
		 */
		private boolean isOwnWork;

		/**
		 * Categories to add to the output text.
		 */
		protected ArrayList<String> cats;

		/**
		 * Constructor, creates a TransferObject
		 * 
		 * @param wpFN The enwp title to transfer
		 * @param comFN The commons title to transfer to
		 * @param isOwnWork Flag indicating if this file is own work.
		 */
		protected TransferFile(String wpFN, String comFN, boolean isOwnWork)
		{
			this.comFN = comFN;
			this.wpFN = wpFN;
			this.isOwnWork = isOwnWork;

			String baseFN = enwp.nss(wpFN);
			localFN = mtcfiles.resolve(baseFN.hashCode() + baseFN.substring(baseFN.lastIndexOf('.')));
		}

		/**
		 * Attempts to transfer an enwp file to Commons
		 * 
		 * @return True on success.
		 */
		protected boolean doTransfer()
		{
			try
			{
				imgInfoL = enwp.getImageInfo(wpFN);
				uploader = imgInfoL.get(imgInfoL.size() - 1).user;

				String text = gen();

				if (dryRun)
				{
					System.out.println(text);
					return true;
				}

				return text != null && downloadFile(httpClient, imgInfoL.get(0).url.toString(), localFN)
						&& com.upload(localFN, comFN, text, MStrings.tFrom)
						&& enwp.edit(wpFN,
								String.format("{{subst:ncd|%s|reviewer=%s}}%n", comFN, enwp.whoami())
										+ enwp.getPageText(wpFN).replaceAll(mtcRegex, ""),
								MStrings.tTo)
						&& (!deleteOnTransfer
								|| enwp.delete(wpFN, String.format("[[WP:CSD#F8|F8]]: Media file available on Commons: [[:%s]]", comFN)));
			}
			catch (Throwable e)
			{
				e.printStackTrace();
				return false;
			}
		}

		/**
		 * Processes parsed text and templates from the API
		 */
		private String gen()
		{
			// preprocess text
			String txt = enwp.getPageText(wpFN);
			txt = txt.replaceAll("(?s)\\<!\\-\\-.*?\\-\\-\\>", ""); // strip comments
			txt = txt.replaceAll("(?i)\\n?\\[\\[(Category:).*?\\]\\]", ""); // categories don't transfer well.
			txt = txt.replaceAll("\\n?\\=\\=.*?\\=\\=\\n?", ""); // strip headers
			txt = txt.replaceAll("(?si)\\{\\|\\s*?class\\=\"wikitable.+?\\|\\}", ""); // strip captions
			txt = txt.replaceAll("(?si)\\{\\{(bots|nobots).*?\\}\\}", ""); // strip nobots

			WikiText docRoot = WParser.parseText(enwp, txt);
			ArrayList<WTemplate> masterTPL = docRoot.getTemplatesR();

			// Normalize template titles
			masterTPL.forEach(t -> {
				t.normalizeTitle(enwp);

				if (tpMap.containsKey(t.title))
					t.title = tpMap.get(t.title);
			});

			// Filter Templates which are not on Commons
			MQuery.exists(com, FL.toAL(
					masterTPL.stream().filter(t -> !ctpCache.containsKey(t.title)).map(t -> com.convertIfNotInNS(t.title, NS.TEMPLATE))))
					.forEach((k, v) -> ctpCache.put(com.nss(k), v));
			masterTPL.removeIf(t -> {
				if (ctpCache.containsKey(t.title) && !ctpCache.get(t.title))
				{
					t.drop();
					return true;
				}
				return false;
			});

			// Transform special Templates
			WTemplate info = null;
			for (WTemplate t : masterTPL)
				switch (t.title)
				{
					case "Information":
						info = t;
						break;
					case "Self":
						if (!t.has("author"))
							t.put("author", String.format("{{User at project|%s|w|en}}", uploader));
						break;
					case "PD-self":
						t.title = "PD-user-en";
						t.put("1", uploader);
						break;
					case "GFDL-self-with-disclaimers":
						t.title = "GFDL-user-en-with-disclaimers";
						t.put("1", uploader);
						break;
					case "GFDL-self":
						t.title = "GFDL-self-en";
						t.put("author", String.format("{{User at project|%s|w|en}}", uploader));
						break;
					default:
				}

			if (info != null)
			{
				masterTPL.remove(info);
				info.drop();
			}

			// Add any Commons-compatible top-level templates to License section.
			masterTPL.retainAll(docRoot.getTemplates());
			masterTPL.forEach(t -> {
				licSection.append(String.format("%s%n", t));
				t.drop();
			});

			// fill-out an Information Template
			sumSection.append(String.format(
					"{{Information\n|description=%s\n|source=%s\n|date=%s\n|" + "author=%s\n|permission=%s\n|other_versions=%s\n}}\n",
					fuzzForParam(info, "Description", "") + docRoot.toString().trim(),
					fuzzForParam(info, "Source", isOwnWork ? "{{Own work by original uploader}}" : "").trim(),
					fuzzForParam(info, "Date", "").trim(),
					fuzzForParam(info, "Author", isOwnWork ? String.format("[[User:%s|%s]]", uploader, uploader) : "").trim(),
					fuzzForParam(info, "Permission", "").trim(), fuzzForParam(info, "Other_versions", "").trim()));

			// Work with text as String
			String out = sumSection.toString() + licSection.toString();
			out = out.replaceAll("(?<=\\[\\[)(.+?\\]\\])", "w:$1"); // add enwp prefix to links
			out = out.replaceAll("(?i)\\[\\[(w::|w:w:)", "[[w:"); // Remove any double colons in interwiki links
			out = out.replaceAll("\\n{3,}", "\n"); // Remove excessive spacing

			// Generate Upload Log Section
			out += "\n== {{Original upload log}} ==\n" + String.format("{{Original file page|en.wikipedia|%s}}%n", enwp.nss(wpFN))
					+ "{| class=\"wikitable\"\n! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} "
					+ "!! {{int:filehist-comment}}";

			for (ImageInfo ii : imgInfoL)
				out += String.format("%n|-%n| %s || %d × %d || [[w:User:%s|%s]] || ''<nowiki>%s</nowiki>''",
						FSystem.iso8601dtf.format(LocalDateTime.ofInstant(ii.timestamp, ZoneOffset.UTC)), ii.dimensions.x, ii.dimensions.y,
						ii.user, ii.user, ii.summary.replace("\n", " ").replace("  ", " "));
			out += "\n|}\n";

			if (cats == null || cats.isEmpty())
				out += "\n{{Subst:Unc}}";
			else
				for (String s : cats)
					out += String.format("\n[[%s]]", s);

			if (useTrackingCat)
				out += "\n[[Category:Uploaded with MTC!]]";

			return out;
		}

		/**
		 * Fuzz for a parameter in an Information template.
		 * 
		 * @param t The Information Template as a WTemplate
		 * @param k The key to look for. Use a capitalized form first.
		 * @param defaultP The default String to return if {@code k} and its variations were not found in {@code t}
		 * @return The parameter, as a String, or {@code defaultP} if the parameter could not be found.
		 */
		private String fuzzForParam(WTemplate t, String k, String defaultP)
		{
			String fzdKey = k;
			return t != null && (t.has(fzdKey) || t.has(fzdKey = k.toLowerCase()) || t.has(fzdKey = fzdKey.replace('_', ' ')))
					? t.get(fzdKey).toString()
					: defaultP;
		}
	}
}