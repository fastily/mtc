package mtc;

import java.nio.file.Path;
import java.nio.file.Paths;

import fastily.jwiki.util.FSystem;

/**
 * Configurable, frequently used constant Strings for MTC.
 * 
 * @author Fastily
 *
 */
public class MStrings
{
	/**
	 * Version number
	 */
	protected static final String version = "1.0.2";

	/**
	 * Short name for MTC!
	 */
	protected static final String name = "MTC!"; 
	
	/**
	 * The full name of MTC!, with the namespace prefix.
	 */
	protected static final String fullname = "Wikipedia:" + name;
	
	/**
	 * The directory pointing to the location for file downloads
	 */
	protected static final String fdump = "mtcfiles" + FSystem.psep;

	/**
	 * The Path object pointing to {@code fdump}
	 */
	protected static final Path fdPath = Paths.get(fdump);

	/**
	 * Format String edit summary for files uploaded to Commons for Commons
	 */
	protected static final String tFrom = String.format("Transferred from en.wikipedia ([[w:%s|%s]]) (%s)", fullname, name, version);

	/**
	 * Edit summary for files transferred to Commons on enwp
	 */
	protected static final String tTo = String.format("Transferred to Commons ([[%s|%s]]) (%s)", fullname, name, version);
		
	/**
	 * Speedy deletion F8 reason format string.
	 */
	protected static final String f8Fmt = "[[WP:CSD#F8|F8]]: Media file available on Commons: [[:%s]]";

	/**
	 * The format String for a row in the Upload Log section.
	 */
	protected static final String uLFmt = "%n|-%n| %s || %d × %d || [[w:User:%s|%s]] || ''<nowiki>%s</nowiki>''";

	/**
	 * Constructors disallowed.
	 */
	private MStrings()
	{

	}
}