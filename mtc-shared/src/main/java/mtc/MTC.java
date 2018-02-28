package mtc;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import fastily.jwiki.core.MQuery;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.WParser;
import fastily.jwiki.core.WParser.WTemplate;
import fastily.jwiki.core.WParser.WikiText;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.dwrap.ImageInfo;
import fastily.jwiki.util.FL;
import fastily.jwiki.util.FSystem;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Business Logic for MTC. Contains shared methods, constants, and Objects.
 * 
 * @author Fastily
 *
 */
public class MTC
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
	protected Wiki enwp, com;

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
	 * Flag indicating whether transferred files should include the check needed category.
	 */
	protected boolean useCheckNeededCat = false;
	
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
	private OkHttpClient httpClient = new OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).build();
	
	/**
	 * Creates an MTC object.
	 * 
	 * @param cliOnly Set true to disable download folder creation. CAVEAT: This is only for read-only usage.
	 */
	public MTC(Wiki enwp, Wiki com)
	{
		this.enwp = enwp;
		this.com = com;
		
		// Generate whitelist & blacklist
		HashMap<String, ArrayList<String>> l = MQuery.getLinksOnPage(enwp,
				FL.toSAL(MStrings.fullname + "/Blacklist", MStrings.fullname + "/Whitelist"));
		blacklist = new HashSet<>(l.get(MStrings.fullname + "/Blacklist"));
		whitelist = new HashSet<>(l.get(MStrings.fullname + "/Whitelist"));

		// Generate download directory
		try //TODO: Split into own method
		{
			if (!Files.isDirectory(mtcfiles))
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
		mtcRegex = "(?si)\\{\\{(" + FL.pipeFence(rtl) + ").*?\\}\\}";
	}

	/**
	 * Creates TransferFile obejcts from a List of titles. Also filters (if enabled) and auto-resolves Commons filenames
	 * for transfer candidates.
	 * 
	 * @param titles The List of enwp files to transfer
	 * @return An ArrayList of TransferObject objects.
	 */
	public ArrayList<FileInfo> makeTransferFile(ArrayList<String> titles)
	{
		HashMap<String, ArrayList<String>> catL = MQuery.getCategoriesOnPage(enwp, titles);
		if (!ignoreFilter)
			catL.forEach((k, v) -> {
				if (v.stream().anyMatch(blacklist::contains) || !v.stream().anyMatch(whitelist::contains))
					titles.remove(k);
			});
		
		MQuery.getSharedDuplicatesOf(enwp, titles).forEach((k, v) -> {
			if (!v.isEmpty())
				titles.remove(k);
		});

		ArrayList<FileInfo> l = new ArrayList<>();
		MQuery.exists(com, titles).forEach((k, v) -> {
			if (!v)
				l.add(new FileInfo(k, k, catL.get(k)));
			else
			{
				String comFN;
				do
				{
					comFN = new StringBuilder(k).insert(k.lastIndexOf('.'), " " + Math.round(Math.random() * 1000)).toString();
				} while (com.exists(comFN)); // loop until available filename is found

				l.add(new FileInfo(k, comFN, catL.get(k)));
			}
		});

		return l;
	}

	/**
	 * Downloads a file and saves it to disk.
	 * 
	 * @param client The OkHttpClient to use perform network connections with.
	 * @param u The url to download from
	 * @param localpath The local path to save the file at.
	 * @return True on success.
	 */
	private static boolean downloadFile(OkHttpClient client, HttpUrl u, Path localpath)
	{
		System.err.println("Downloading a file to " + localpath);

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
	public class FileInfo
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
		 * Cached file description text on enwp.
		 */
		private String enwpText;

		/**
		 * The output text for Commons.
		 */
		protected String comText;
		
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
		private ArrayList<String> cats = new ArrayList<>();

		/**
		 * Constructor, creates a TransferObject
		 * 
		 * @param wpFN The enwp title to transfer
		 * @param comFN The commons title to transfer to
		 * @param enwpCats List of categories on the enwp file description page
		 */
		private FileInfo(String wpFN, String comFN, ArrayList<String> enwpCats)
		{
			this.comFN = comFN;
			this.wpFN = wpFN;
			this.isOwnWork = enwpCats.contains("Category:Self-published work");

			String baseFN = enwp.nss(wpFN);
			localFN = mtcfiles.resolve(baseFN.hashCode() + baseFN.substring(baseFN.lastIndexOf('.')));
			
			if(useCheckNeededCat)
				cats.add(String.format("Category:Files uploaded by %s with MTC! (check needed)", enwp.whoami()));
		}

		/**
		 * Adds categories which will be applied to transferred files.
		 * @param catL The categories to add.
		 */
		public void addCat(String...catL)
		{
			cats.addAll(Arrays.asList(catL));
		}
		
		/**
		 * Attempts to transfer an enwp file to Commons
		 * 
		 * @return True on success.
		 */
		public boolean doTransfer()
		{
			try
			{
				if(comText == null)
					gen();

				if (dryRun)
				{
					System.out.println(comText);
					return true;
				}

				return comText != null && downloadFile(httpClient, imgInfoL.get(0).url, localFN)
						&& com.upload(localFN, comFN, comText, MStrings.tFrom)
						&& enwp.edit(wpFN, String.format("{{subst:ncd|%s|reviewer=%s}}%n", comFN, enwp.whoami()) + enwpText, MStrings.tTo)
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
		public void gen()
		{
			if(comText != null)
				return;
			
			imgInfoL = enwp.getImageInfo(wpFN);
			uploader = imgInfoL.get(imgInfoL.size() - 1).user;
			
			// preprocess text
			String txt = enwp.getPageText(wpFN);
			txt = txt.replaceAll(mtcRegex, ""); // strip copy to commons

			enwpText = new String(txt); // cache description page text

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
					"{{Information\n|description=%s\n|source=%s\n|date=%s\n|author=%s\n|permission=%s\n|other_versions=%s\n}}\n",
					fuzzForParam(info, "Description", "") + docRoot.toString().trim(),
					fuzzForParam(info, "Source", isOwnWork ? "{{Own work by original uploader}}" : "").trim(),
					fuzzForParam(info, "Date", "").trim(),
					fuzzForParam(info, "Author", isOwnWork ? String.format("[[User:%s|%s]]", uploader, uploader) : "").trim(),
					fuzzForParam(info, "Permission", "").trim(), fuzzForParam(info, "Other_versions", "").trim()));

			// Work with text as String
			comText = sumSection.toString() + licSection.toString();
			comText = comText.replaceAll("(?<=\\[\\[)(.+?\\]\\])", "w:$1"); // add enwp prefix to links
			comText = comText.replaceAll("(?i)\\[\\[(w::|w:w:)", "[[w:"); // Remove any double colons in interwiki links
			comText = comText.replaceAll("\\n{3,}", "\n"); // Remove excessive spacing

			// Generate Upload Log Section
			comText += "\n== {{Original upload log}} ==\n" + String.format("{{Original file page|en.wikipedia|%s}}%n", enwp.nss(wpFN))
					+ "{| class=\"wikitable\"\n! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} "
					+ "!! {{int:filehist-comment}}";

			for (ImageInfo ii : imgInfoL)
				comText += String.format("%n|-%n| %s || %d × %d || [[w:User:%s|%s]] || ''<nowiki>%s</nowiki>''",
						FSystem.iso8601dtf.format(LocalDateTime.ofInstant(ii.timestamp, ZoneOffset.UTC)), ii.width, ii.height,
						ii.user, ii.user, ii.summary.replace("\n", " ").replace("  ", " "));
			comText += "\n|}\n";

			// Fill in cats
			if (cats.isEmpty())
				comText += "\n{{Subst:Unc}}";
			else
				for (String s : cats)
					comText += String.format("\n[[%s]]", com.convertIfNotInNS(s, NS.CATEGORY));

			if (useTrackingCat)
				comText += "\n[[Category:Uploaded with MTC!]]";
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