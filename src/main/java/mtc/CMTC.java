package mtc;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import fastily.jwiki.core.ColorLog;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.util.FL;
import fastily.wpkit.util.FCLI;
import fastily.wpkit.util.WikiGen;

/**
 * Command line interface for MTC.
 * 
 * @author Fastily
 *
 */
public class CMTC
{
	/**
	 * Main driver
	 * 
	 * @param args Program args
	 */
	public static void main(String[] args) throws Throwable
	{
		CommandLine l = FCLI.gnuParse(makeOptList(), args, "MTC [-help] [-f] [-d] [-s] [<titles|user|cat>]");

		Wiki wiki = WikiGen.wg.get("FSock", "en.wikipedia.org");
		// Do initial logins, and generate MTC regexes
		MTC mtc = new MTC(wiki);
		mtc.useTrackingCat = false;
		mtc.dryRun = l.hasOption('d');

		if (l.hasOption('f'))
			mtc.ignoreFilter = true;

		ArrayList<String> fl = new ArrayList<>();
		if (l.hasOption('s'))
			fl = wiki.getLinksOnPage(String.format("User:%s/MTCSources/List", wiki.whoami()), NS.FILE);
		else
			for (String s : l.getArgs())
			{				
				NS ns = wiki.whichNS(s);
				if (ns.equals(NS.FILE))
					fl.add(s);
				else if (ns.equals(NS.CATEGORY))
					fl.addAll(wiki.getCategoryMembers(s, NS.FILE));
				else if (ns.equals(NS.TEMPLATE))
					fl.addAll(wiki.whatTranscludesHere(s, NS.FILE));
				else
					fl.addAll(wiki.getUserUploads(s));
			}

		ArrayList<MTC.TransferFile> tl = mtc.filterAndResolve(fl);
		int total = tl.size();
		AtomicInteger i = new AtomicInteger();

		ArrayList<String> fails = FL.toAL(tl.stream().filter(to -> {
			ColorLog.fyi(String.format("Processing item %d of %d", i.incrementAndGet(), total));
			return !to.doTransfer();
		}).map(to -> to.wpFN));

		System.out.printf("Task complete, with %d failures: %s%n", fails.size(), fails);
	}

	/**
	 * Makes the list of CLI options.
	 * 
	 * @return The list of CommandLine options.
	 */
	private static Options makeOptList()
	{
		Options ol = FCLI.makeDefaultOptions();
		ol.addOption("f", "Force (ignore filter) file transfer(s)");
		ol.addOption("s", "Use current user's MTC Sources Page - overrides other source input methods");
		ol.addOption("d", "Activate dry run/debug mode (does not transfer files)");
		return ol;
	}
}