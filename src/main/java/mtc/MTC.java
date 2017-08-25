package mtc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import fastily.jwiki.core.MQuery;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.dwrap.ImageInfo;
import fastily.jwiki.util.FL;
import fastily.jwiki.util.FSystem;
import fastily.wpkit.text.ReportUtils;
import fastily.wpkit.text.WTP;
import fastily.wpkit.tp.WParser;
import fastily.wpkit.tp.WTemplate;
import fastily.wpkit.tp.WikiText;

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
	 * Format String for Information template
	 */
	protected static String 	infoT = "{{Information\n|description=%s\n|source=%s\n|date=%s\n|"
			+ "author=%s\n|permission=%s\n|other_versions=%s\n}}\n";
	
	/**
	 * Regex matching Copy to Commons templates.
	 */
	protected final String mtcRegex;

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
	protected HashMap<String, String> tpMap;
	
	/**
	 * Files with these categories should not be transferred.
	 */
	protected HashSet<String> blacklist;

	/**
	 * Files must be members of at least one of the following categories to be eligible for transfer.
	 */
	protected HashSet<String> whitelist;

	/**
	 * The Wiki objects to use
	 */
	protected Wiki enwp, com;

	/**
	 * Initializes the Wiki objects and download folders for MTC.
	 * 
	 * @param enwp A logged-in Wiki object, set to {@code en.wikipedia.org}
	 * 
	 * @throws Throwable On IO error
	 */
	public MTC(Wiki enwp) throws Throwable
	{
		// Initialize Wiki objects
		this.enwp = enwp;
		com = enwp.getWiki("commons.wikimedia.org");

		mtcRegex = WTP.mtc.getRegex(enwp);
		
		// Generate whitelist & blacklist
		HashMap<String, ArrayList<String>> l = MQuery.getLinksOnPage(enwp,
				FL.toSAL(MStrings.fullname + "/Blacklist", MStrings.fullname + "/Whitelist"));
		blacklist = new HashSet<>(l.get(MStrings.fullname + "/Blacklist"));
		whitelist = new HashSet<>(l.get(MStrings.fullname + "/Whitelist"));

		// Generate download directory
		if (Files.isRegularFile(MStrings.fdPath))
			FSystem.errAndExit(MStrings.fdump + " is file, please remove it so MTC can continue");
		else if (!Files.isDirectory(MStrings.fdPath))
			Files.createDirectory(MStrings.fdPath); //TODO: Use temp dirs
		
		tpMap = initNormalizerMap("Redirects");
	}

	protected HashMap<String, String> initNormalizerMap(String pg)
	{
		HashMap<String, String> l = new HashMap<>();
		for (String line : enwp.getPageText(MStrings.fullname + "/" + pg).split("\n"))
			if (!line.startsWith("<") && !line.isEmpty())
			{
				String[] splits = line.split("\\|");
				for (String s : splits)
					l.put(s, splits[0]);
			}
		
		return l;
	}
	
	/**
	 * Filters (if enabled) and resolves Commons filenames for transfer candidates
	 * 
	 * @param titles The local files to transfer
	 * @return An ArrayList of TransferObject objects.
	 */
	protected ArrayList<TransferFile> filterAndResolve(ArrayList<String> titles)
	{
		MQuery.getSharedDuplicatesOf(enwp, titles).forEach((k, v) -> {
			if (!v.isEmpty())
				titles.remove(k);
		});

		HashMap<String, ArrayList<String>> catL = MQuery.getCategoriesOnPage(enwp, titles);
		
		if(!ignoreFilter)
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
					comFN = Utils.permuteFileName(k);
				} while (com.exists(comFN)); // loop until available filename is found

				m.put(k, comFN);
			}
		});

		return m;
	}

	/**
	 * Filters files which obviously cannot be transferred to Commons.
	 * 
	 * @param titles The titles to check.
	 * @return An ArrayList with files that are most likely eligible for Commons.
	 */
	public ArrayList<String> canTransfer(ArrayList<String> titles)
	{
		ArrayList<String> l = new ArrayList<>();
		MQuery.getSharedDuplicatesOf(enwp, titles).forEach((k, v) -> {
			if (v.size() == 0)
				l.add(k);
		});

		ArrayList<String> rl = new ArrayList<>();
		MQuery.getCategoriesOnPage(enwp, l).forEach((k, v) -> {
			if (!v.stream().anyMatch(blacklist::contains) && v.stream().anyMatch(whitelist::contains))
				rl.add(k);
		});

		return rl;
	}

	/**
	 * Represents various supported file transfer modes.
	 * 
	 * @author Fastily
	 *
	 */
	protected enum TransferMode
	{
		/**
		 * Represents the single file transfer mode.
		 */
		FILE("File"),

		/**
		 * Represents category mass-transfer mode.
		 */
		CATEGORY("Category"),

		/**
		 * Represents user uploads mass-transfer mode.
		 */
		USER("User"),

		/**
		 * Represents template transclusions mass-transfer mode.
		 */
		TEMPLATE("Template"),

		/**
		 * Represents all file links on a page mass-transfer mode.
		 */
		FILELINKS("Filelinks"),

		/**
		 * Represents all file namespace links on a page mass-transfer mode.
		 */
		LINKS("Links");

		/**
		 * Constructor, creates a new TransferMode.
		 * 
		 * @param name The user-suitable name to create this TransferMode with.
		 */
		private TransferMode(String name)
		{
			this.name = name;
		}

		/**
		 * The user-suitable name of this TransferMode.
		 */
		private String name;

		/**
		 * Returns the user-suitable name of this TransferMode.
		 */
		public String toString()
		{
			return name;
		}
	}

	/**
	 * Represents a file to transfer to Commons
	 * 
	 * @author Fastily
	 *
	 */
	protected class TransferFile
	{
		/**
		 * The enwp filename
		 */
		protected String wpFN;

		/**
		 * The commons filename and local path
		 */
		private String comFN, localFN;

		/**
		 * The root WikiText object parsed from this file's enwp page. 
		 */
		private WikiText root;
		
		/**
		 * The summary and license sections.
		 */
		private StringBuilder sumSection = new StringBuilder("== {{int:filedesc}} ==\n"), licSection = new StringBuilder("\n== {{int:license-header}} ==\n");

		/**
		 * The list of old revisions for the file
		 */
		private ArrayList<ImageInfo> imgInfoL;

		/**
		 * The user who originally uploaded the file. Excludes <code>User:</code> prefix.
		 */
		private String uploader;

		/**
		 * Flag indicating if this file contains own work templates.
		 */
		private boolean isOwnWork;

		/**
		 * Constructor, creates a TransferObject
		 * 
		 * @param wpFN The enwp title to transfer
		 * @param comFN The commons title to transfer to
		 */
		protected TransferFile(String wpFN, String comFN, boolean isOwnWork)
		{
			this.comFN = comFN;
			this.wpFN = wpFN;
			this.isOwnWork = isOwnWork;

			String baseFN = enwp.nss(wpFN);
			localFN = MStrings.fdump + baseFN.hashCode() + baseFN.substring(baseFN.lastIndexOf('.'));
		}
		
		protected WikiText preprocess()
		{
			String t = enwp.getPageText(wpFN);
			t = t.replaceAll("(?s)\\<!\\-\\-.*?\\-\\-\\>", ""); // strip comments
			t = t.replaceAll("(?i)\\n?\\[\\[(Category:).*?\\]\\]", ""); // categories don't transfer well.
			t = t.replaceAll("\\n?\\=\\=.*?\\=\\=\\n?", ""); // strip headers
			t = t.replaceAll("(?si)\\{\\|\\s*?class\\=\"wikitable.+?\\|\\}", ""); // strip captions
			
			return WParser.parseText(enwp, t);
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
				root = preprocess();

				imgInfoL = enwp.getImageInfo(wpFN);
				uploader = imgInfoL.get(imgInfoL.size() - 1).user;

				procText();
				String t = gen();

				if (dryRun)
				{
					System.out.println(t);
					return true;
				}

				return t != null && Utils.downloadFile(enwp.apiclient.client, imgInfoL.get(0).url.toString(), localFN)
						&& com.upload(Paths.get(localFN), comFN, t, MStrings.tFrom)
						&& enwp.edit(wpFN,
								String.format("{{subst:ncd|%s|reviewer=%s}}%n", comFN, enwp.whoami())
										+ enwp.getPageText(wpFN).replaceAll(mtcRegex, ""),
								MStrings.tTo)
						&& ( !deleteOnTransfer || enwp.delete(wpFN, String.format(MStrings.f8Fmt, comFN)) );
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
		private void procText()
		{
			ArrayList<WTemplate> masterTPL = root.getTemplatesR();

			// Normalize template titles
			for (WTemplate t : masterTPL)
			{
				String tp = enwp.whichNS(t.title).equals(NS.TEMPLATE) ? enwp.nss(t.title) : t.title; //Remove 'Template:' prefix
				tp = tp.replace('_', ' ');
				tp = tp.length() <= 1 ? tp.toUpperCase() : "" + Character.toUpperCase(tp.charAt(0)) + tp.substring(1);
				
				if (tpMap.containsKey(tp))
					t.title = tpMap.get(tp);
			}

			// Filter Templates which are not on Commons
			MQuery.exists(com, FL.toAL(masterTPL.stream().map(t -> com.convertIfNotInNS(t.title, NS.TEMPLATE)))).forEach((k, v) -> ctpCache.put(com.nss(k), v)); //TODO: implement cache
			masterTPL.removeIf(t -> {
				if(ctpCache.containsKey(t.title) && !ctpCache.get(t.title))
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
			
			if(info != null)
			{
				masterTPL.remove(info);
				info.drop();
			}

			// Add any Commons-compatible top-level templates to License section.
			masterTPL.retainAll(root.getTemplates());
			masterTPL.forEach(t -> {
				licSection.append(String.format("%s%n", t));
				t.drop();
			});
			
			// fill-out an Information Template
			sumSection.append(String.format(infoT, 
					fuzzForParam(info, "Description", "") + root.toString().trim(),
					fuzzForParam(info, "Source", isOwnWork ? "{{Own work by original uploader}}" : "").trim(),
					fuzzForParam(info, "Date", "").trim(),
					fuzzForParam(info, "Author", isOwnWork ? String.format("[[User:%s|%s]]", uploader, uploader) : "").trim(),
					fuzzForParam(info, "Permission", "").trim(),
					fuzzForParam(info, "Other_versions", "").trim()
					));
		}

		/**
		 * Fuzz for a parameter in an Information template.
		 * @param t The Information Template as a WTemplate
		 * @param k The key to look for.  Use a capitalized form first.
		 * @param defaultP The default String to return if {@code k} and its variations were not found in {@code t}
		 * @return The parameter, as a String, or {@code defaultP} if the parameter could not be found.
		 */
		private String fuzzForParam(WTemplate t, String k, String defaultP)
		{
			String fzdKey = k;
			return t != null && (t.has(fzdKey) || t.has(fzdKey = k.toLowerCase()) || t.has(fzdKey = fzdKey.replace('_', ' '))) ? t.get(fzdKey).toString() : defaultP;
		}
		
		/**
		 * Renders this TransferFile as wikitext for Commons.
		 */
		private String gen()
		{
			String t = sumSection.toString() + licSection.toString();

			t = t.replaceAll("(?<=\\[\\[)(.+?\\]\\])", "w:$1"); // add enwp prefix to links
			t = t.replaceAll("(?i)\\[\\[(w::|w:w:)", "[[w:"); // Remove any double colons in interwiki links
			t = t.replaceAll("\\n{3,}", "\n"); // Remove excessive spacing
			
			// Generate Upload Log Section
			try
			{
				t += "\n== {{Original upload log}} ==\n" + String.format("{{Original file page|en.wikipedia|%s}}%n", enwp.nss(wpFN))
						+ "{| class=\"wikitable\"\n! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} "
						+ "!! {{int:filehist-comment}}";
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}

			for (ImageInfo ii : imgInfoL)
				t += String.format(MStrings.uLFmt, ReportUtils.iso8601dtf.format(LocalDateTime.ofInstant(ii.timestamp, ZoneOffset.UTC)),
						ii.dimensions.x, ii.dimensions.y, ii.user, ii.user, ii.summary.replace("\n", " ").replace("  ", " "));
			t += "\n|}\n\n{{Subst:Unc}}";

			if (useTrackingCat)
				t += "\n[[Category:Uploaded with MTC!]]";

			return t;
		}
	}
}