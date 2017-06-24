package mtc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.regex.Matcher;

import ctools.tplate.ParsedItem;
import ctools.tplate.Template;
import ctools.util.Toolbox;
import ctools.util.WikiX;
import enwp.WPStrings;
import enwp.WTP;
import fastily.jwiki.core.MQuery;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.dwrap.ImageInfo;
import fastily.jwiki.util.FL;
import fastily.jwiki.util.FSystem;

/**
 * Business Logic for MTC. Contains shared methods, constants, and Objects.
 * 
 * @author Fastily
 *
 */
public final class MTC
{
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
	 * Contains data for license tags
	 */
	protected TreeMap<String, String> tpMap = new TreeMap<>(WikiX.tpParamCmp);

	/**
	 * Files with these categories should not be transferred.
	 */
	protected HashSet<String> blacklist;

	/**
	 * Files must be members of at least one of the following categories to be eligible for transfer.
	 */
	protected HashSet<String> whitelist;

	/**
	 * Templates which indicate that a file is own work.
	 */
	protected HashSet<String> selflist;

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
		com = Toolbox.getCommons(enwp);

		// Generate whitelist & blacklist
		HashMap<String, ArrayList<String>> l = MQuery.getLinksOnPage(enwp,
				FL.toSAL(MStrings.fullname + "/Blacklist", MStrings.fullname + "/Whitelist", MStrings.fullname + "/Self"));
		blacklist = new HashSet<>(l.get(MStrings.fullname + "/Blacklist"));
		whitelist = new HashSet<>(l.get(MStrings.fullname + "/Whitelist"));
		selflist = FL.toSet(l.get(MStrings.fullname + "/Self").stream().map(enwp::nss));

		// Generate download directory
		if (Files.isRegularFile(MStrings.fdPath))
			FSystem.errAndExit(MStrings.fdump + " is file, please remove it so MTC can continue");
		else if (!Files.isDirectory(MStrings.fdPath))
			Files.createDirectory(MStrings.fdPath);

		mtcRegex = WTP.mtc.getRegex(enwp);

		// Process template data
		Toolbox.fetchPairedConfig(enwp, MStrings.fullname + "/Regexes").forEach((k, v) -> {
			String t = enwp.nss(k);
			for (String s : v.split("\\|"))
				tpMap.put(s, t);
		});
	}

	/**
	 * Filters (if enabled) and resolves Commons filenames for transfer candidates
	 * 
	 * @param titles The local files to transfer
	 * @return An ArrayList of TransferObject objects.
	 */
	protected ArrayList<TransferFile> filterAndResolve(ArrayList<String> titles)
	{
		ArrayList<TransferFile> l = new ArrayList<>();
		resolveFileNames(!ignoreFilter ? canTransfer(titles) : titles).forEach((k, v) -> l.add(new TransferFile(k, v)));
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
					comFN = Toolbox.permuteFileName(k);
				} while (com.exists(comFN)); // loop until available filename is found

				m.put(k, comFN);
			}
		});

		return m;
	}

	/**
	 * Filters files which obviously cannot be transferred to Commons.
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
		protected final String wpFN;

		/**
		 * The commons filename and local path
		 */
		private final String comFN, localFN;

		/**
		 * The root ParsedItem for the file's enwp page.
		 */
		private ParsedItem root;

		/**
		 * The summary and license sections.
		 */
		private String sumSection = "== {{int:filedesc}} ==\n", licSection = "\n== {{int:license-header}} ==\n";

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
		protected TransferFile(String wpFN, String comFN)
		{
			this.comFN = comFN;
			this.wpFN = wpFN;

			String baseFN = enwp.nss(wpFN);
			localFN = MStrings.fdump + baseFN.hashCode() + baseFN.substring(baseFN.lastIndexOf('.'));
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
				root = ParsedItem.parse(enwp, wpFN);

				imgInfoL = enwp.getImageInfo(wpFN);
				uploader = imgInfoL.get(imgInfoL.size() - 1).user;

				procText();
				String t = gen();

				if (dryRun)
				{
					System.out.println(t);
					return true;
				}

				return t != null && Toolbox.downloadFile(enwp.apiclient.client, imgInfoL.get(0).url.toString(), localFN)
						&& com.upload(Paths.get(localFN), comFN, t, String.format(MStrings.tFrom, wpFN))
						&& enwp.edit(wpFN,
								String.format("{{subst:ncd|%s|reviewer=%s}}%n", comFN, enwp.whoami())
										+ enwp.getPageText(wpFN).replaceAll(mtcRegex, ""),
								MStrings.tTo)
						&& (deleteOnTransfer ? enwp.delete(wpFN, String.format(MStrings.f8Fmt, comFN)) : true);
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
			ArrayList<Template> masterTPL = root.getTemplateR();

			// Normalize license and special template titles
			for (Template t : masterTPL)
			{
				String tp = enwp.whichNS(t.title).equals(NS.TEMPLATE) ? enwp.nss(t.title) : t.title;
				if (tpMap.containsKey(tp))
					t.title = tpMap.get(tp);
			}

			// Check for self-work claim.
			isOwnWork = masterTPL.stream().anyMatch(t -> selflist.contains(t.title));

			ArrayList<Template> tpl = new ArrayList<>(masterTPL);

			// Filter Templates which are not on Commons
			HashSet<String> ncomT = FL
					.toSet(MQuery.exists(com, false, FL.toAL(tpl.stream().map(t -> "Template:" + t.title))).stream().map(com::nss));

			for (Template t : new ArrayList<>(tpl))
				if (ncomT.contains(t.title))
					tpl.remove(t.drop());

			// Process special Templates
			Template info = null;
			for (Template t : new ArrayList<>(tpl))
				switch (t.title)
				{
					case "Information":
						info = t;
						tpl.remove(t.drop());
						break;
					case "Multilicense replacing placeholder":
					case "Multilicense replacing placeholder new":
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
					case "Copy to Wikimedia Commons":
						tpl.remove(t.drop());
						break;
					default:
						break;
				}

			// Add any Commons-compatible top-level templates to License section.
			tpl.retainAll(root.tplates);
			for (Template t : tpl)
				licSection += String.format("%s%n", t);

			// Create and fill in missing {{Information}} fields with default values.
			info = filterFillInfo(info == null ? new Template("Information") : info);

			// Append any additional Strings to the description.
			if (!root.contents.isEmpty())
				info.append("Description", "\n" + String.join("\n", root.contents));

			// Convert {{Information}} to String and save result.
			sumSection += info.toString(true) + "\n";

			// Extract the first caption table and move it to the end of the sumSection
			String x = "";
			Matcher m = MStrings.captionRegex.matcher(sumSection);
			if (m.find())
			{
				x = m.group().trim();
				sumSection = m.reset().replaceAll("");
				sumSection += x + "\n";
			}
		}

		/**
		 * Filters nonsense {{Information}} parameters and fills in missing default values.
		 * 
		 * @param info The information template
		 * @return A new Template with filtered keys and default values where applicable
		 */
		private Template filterFillInfo(Template info)
		{
			Template t = new Template("Information");

			fillTemplateInfo(t, info, "Description");
			fillTemplateInfo(t, info, "Date");
			fillTemplateInfo(t, info, "Source");
			fillTemplateInfo(t, info, "Author");

			// Optional Parameters
			if (info.has("Permission"))
				t.put("Permission", info.get("Permission"));
			if (info.has("other versions"))
				t.put("other versions", info.get("other versions"));

			// Fill in source and author if needed.
			if (isOwnWork)
			{
				if (t.get("Source").getString().isEmpty())
					t.put("Source", "{{Own work by original uploader}}");

				if (t.get("Author").getString().isEmpty())
					t.put("Author", String.format("[[User:%s|%s]]", uploader, uploader));
			}

			return t;
		}

		/**
		 * Copies a parameter from {@code source} to {@code target} if it exists. Fills in the empty String in {@code target}
		 * if {@code param} did not exist in {@code source}
		 * 
		 * @param target The Template which will have {@code param} copied to it from {@code source}
		 * @param source The Template which will have {@code param} copied from it to {@code target}
		 * @param param The parameter to copy from {@code source} into {@code target}
		 */
		private void fillTemplateInfo(Template target, Template source, String param)
		{
			target.put(param, source.has(param) ? source.get(param) : "");
		}

		/**
		 * Renders this TransferFile as wikitext for Commons.
		 */
		private String gen()
		{
			String t = sumSection + licSection;

			t = t.replaceAll("(?s)\\<!\\-\\-.*?\\-\\-\\>", ""); // strip comments
			t = t.replaceAll("(?i)\\n?\\[\\[(Category:).*?\\]\\]", ""); // categories don't transfer well.
			t = t.replaceAll("(?<=\\[\\[)(.+?\\]\\])", "w:$1"); // add enwp prefix to links
			t = t.replaceAll("(?i)\\[\\[(w::|w:w:)", "[[w:"); // Remove any double colons in interwiki links

			// Generate Upload Log Section
			try
			{
				t += "\n== {{Original upload log}} ==\n"
						+ String.format("{{Original description page|en.wikipedia|%s}}%n", enwp.nss(wpFN))
						+ "{| class=\"wikitable\"\n! {{int:filehist-datetime}} !! {{int:filehist-dimensions}} !! {{int:filehist-user}} "
						+ "!! {{int:filehist-comment}}\n|-\n";
			}
			catch (Throwable e)
			{
				e.printStackTrace();
			}

			for (ImageInfo ii : imgInfoL)
				t += String.format(MStrings.uLFmt, WPStrings.iso8601dtf.format(LocalDateTime.ofInstant(ii.timestamp, ZoneOffset.UTC)), ii.dimensions.x, ii.dimensions.y,
						ii.user, ii.user, ii.summary.replace("\n", " ").replace("  ", " "));
			t += "|}\n\n{{Subst:Unc}}";

			if (useTrackingCat)
				t += "\n[[Category:Uploaded with MTC!]]";

			return t;
		}
	}
}