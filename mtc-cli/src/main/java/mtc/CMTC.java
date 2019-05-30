package mtc;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.util.FL;
import fastily.wptoolbox.WGen;
import mtc.MTC.FileInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command line interface for MTC.
 * 
 * @author Fastily
 *
 */
@Command(name = "CMTC", description = "The Commandline interface for MTC!")
public class CMTC
{
	/**
	 * Flag which causes smart filter to be ignored.
	 */
	@Option(names = { "-f" }, description = "Force (ignore filter) file transfer(s)")
	private boolean ignoreFilter;

	/**
	 * Flag which enables dry run mode.
	 */
	@Option(names = { "-d" }, description = "Activate dry run/debug mode (does not transfer files)")
	private boolean dryRun;

	/**
	 * Parameter which causes the specified category to be added to transferred files.
	 */
	@Option(names = { "-c", "--cat" }, description = "Applies the specified category to transfered files")
	private String cat;

	/**
	 * Flag which causes a check-needed category to be added to transferred files.
	 */
	@Option(names = { "-k", "--checkcat" }, description = "Adds the check needed category to transferred files")
	private boolean addCheckCat;

	/**
	 * Flag which triggers help output
	 */
	@Option(names = { "-h", "--help" }, usageHelp = true, description = "Print this message and exit")
	private boolean helpRequested;

	/**
	 * Injected List which contains non-option elements - files, usernames, or categories.
	 */
	@Parameters(paramLabel = "titles", description = "Files, usernames, templates, or categories")
	private ArrayList<String> l;

	/**
	 * No public constructors
	 */
	private CMTC()
	{

	}

	/**
	 * Main driver
	 * 
	 * @param args Program args
	 */
	public static void main(String[] args) throws Throwable
	{
		CMTC cmtc = CommandLine.populateCommand(new CMTC(), args);
		if (cmtc.helpRequested || cmtc.l == null || cmtc.l.isEmpty())
		{
			CommandLine.usage(cmtc, System.out);
			return;
		}

		Wiki enwp = new Wiki.Builder().withLogin("FSock", WGen.pxFor("FSock")).build();
		MTC mtc = new MTC(enwp, enwp.getWiki(MStrings.comHN));

		mtc.useTrackingCat = false;
		mtc.dryRun = cmtc.dryRun;
		mtc.ignoreFilter = cmtc.ignoreFilter;
		mtc.useCheckNeededCat = cmtc.addCheckCat;

		Wiki wiki = mtc.enwp;

		ArrayList<String> fl = new ArrayList<>();
		for (String s : cmtc.l)
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

		ArrayList<FileInfo> tl = mtc.makeTransferFile(fl);

		if (cmtc.cat != null)
			for (FileInfo t : tl)
				t.addCat(cmtc.cat);

		int total = tl.size();
		AtomicInteger i = new AtomicInteger();

		ArrayList<String> fails = FL.toAL(tl.stream().filter(to -> {
			System.err.println(String.format("Processing item %d of %d", i.incrementAndGet(), total));
			return !to.doTransfer();
		}).map(to -> to.wpFN));

		System.out.printf("Task complete, with %d failures: %s%n", fails.size(), fails);
	}
}