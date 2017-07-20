package mtc;

import java.util.ArrayList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import fastily.jwiki.core.MQuery;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.wpkit.text.ReportUtils;
import fastily.wpkit.text.WTP;
import fastily.wpkit.util.FCLI;
import fastily.wpkit.util.WikiGen;
import fastily.wpkit.util.WikiX;

/**
 * Generates a report where each matching file found. Generated report will be in user's userspace under
 * {@code /MTCSources}.
 * 
 * @author Fastily
 *
 */
public final class DumpFD
{
	/**
	 * The Wiki object to use
	 */
	private static Wiki wiki = WikiGen.wg.get("FSock", "en.wikipedia.org");

	/**
	 * The source to fetch files from. This can be a category, template, or username.
	 */
	private static String fileSource;

	/**
	 * The maximum number of items to generate a report for. Set -1 to disable.
	 */
	private static int max;

	/**
	 * Regexes matching MTC and Orphan image templates.
	 */
	private static String mtcRegex = WTP.mtc.getRegex(wiki), orRegex = WTP.orphan.getRegex(wiki);

	/**
	 * The text to output to {@code mtcSources}
	 */
	private static String output = "Report Generated @ ~~~~~\n{| class=\"wikitable sortable\" style=\"margin-left: auto; margin-right: auto;\"\n! Title \n! File \n! Desc\n|-\n";

	/**
	 * The title to output text to.
	 */
	private static String mtcSources = String.format("User:%s/MTCSources", wiki.whoami());

	/**
	 * Main driver
	 * 
	 * @param args Program args
	 */
	public static void main(String[] args) throws Throwable
	{
		CommandLine cl = FCLI.gnuParse(makeOpts(), args, "DumpFD [-m] [-s]");
		max = Integer.parseInt(cl.getOptionValue('m', "150"));
		fileSource = cl.getOptionValue('s', "Category:Copy to Wikimedia Commons reviewed by a human");

		ArrayList<String> l;
		switch (wiki.whichNS(fileSource).v)
		{
			case 0: // user, but sometimes w/o namespace.
			case 2: // user
				l = wiki.getUserUploads(fileSource);
				break;
			case 10:
				l = wiki.whatTranscludesHere(fileSource, NS.FILE);
				break;
			case 14: // category members
				l = wiki.getCategoryMembers(fileSource, max, NS.FILE);
				break;
			default:
				l = null;
				throw new IllegalArgumentException(fileSource + " is not a valid source for MTC files");
		}

		ArrayList<String> tl = new MTC(wiki).canTransfer(l);
		MQuery.getPageText(wiki, tl).forEach(
				(k, v) -> output += String.format("| [[:%s]]%n%s%n| [[%s|center|200px]]%n| %s%n|-%n", k, WikiX.getPageAuthor(wiki, k), k,
						v.replaceAll("(?m)^\\=\\=.+?\\=\\=$\\s*", "").replaceAll(mtcRegex, "").replaceAll(orRegex, "")));

		output += "|}\n";

		wiki.edit(mtcSources, output, "+");
		wiki.edit(mtcSources + "/List", ReportUtils.listify("", tl, true), "+");
	}

	/**
	 * Creates CLI options for DumpFD
	 * 
	 * @return CLI Options for the application.
	 */
	private static Options makeOpts()
	{
		Options ol = FCLI.makeDefaultOptions();
		ol.addOption(FCLI.makeArgOption("m", "The maximum number of items to fetch", "num"));
		ol.addOption(FCLI.makeArgOption("s", "The source page", "source"));

		return ol;
	}
}