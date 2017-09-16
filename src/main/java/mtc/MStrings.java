package mtc;

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
	protected static final String version = "1.1.0";

	/**
	 * Short name for MTC!
	 */
	protected static final String name = "MTC!";

	/**
	 * The full name of MTC!, with the namespace prefix.
	 */
	protected static final String fullname = "Wikipedia:" + name;

	/**
	 * Format String edit summary for files uploaded to Commons for Commons
	 */
	protected static final String tFrom = String.format("Transferred from en.wikipedia ([[w:%s|%s]]) (%s)", fullname, name, version);

	/**
	 * Edit summary for files transferred to Commons on enwp
	 */
	protected static final String tTo = String.format("Transferred to Commons ([[%s|%s]]) (%s)", fullname, name, version);

	/**
	 * Constructors disallowed.
	 */
	private MStrings()
	{

	}
}