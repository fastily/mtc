package mtc;

import org.fastily.wptoolbox.Sys;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 * Miscellaneous static JavaFX routines
 * 
 * @author Fastily
 *
 */
public final class FXTool
{
	/**
	 * Constructors disallowed
	 */
	private FXTool()
	{

	}

	/**
	 * Creates a new FXMLLoader for the specified class.
	 * 
	 * @param <T> The class to create an FXMLLoader for.
	 * @param name The path (relative to classpath of {@code c}) of the FXML file.
	 * @param c The Class to get a loader for.
	 * @return The FXMLLoader.
	 */
	public static <T> FXMLLoader makeNewLoader(String name, Class<T> c)
	{
		try
		{
			return new FXMLLoader(c.getResource(name));
		}
		catch (Throwable e)
		{
			Sys.errAndExit(e, String.format("Should never reach this point; '%s' is missing?", name));
			return null;
		}
	}

	/**
	 * Shows an alert dialog warning the user.
	 * 
	 * @param msg The message to show the user.
	 */
	public static void warnUser(String msg)
	{
		alertUser(msg, Alert.AlertType.ERROR);
	}

	/**
	 * Shows a dialog for the user.
	 * 
	 * @param msg The mesage to show the user
	 * @param type The message type to show.
	 */
	public static void alertUser(String msg, Alert.AlertType type)
	{
		new Alert(type, msg, ButtonType.OK).showAndWait();
	}

	/**
	 * Launches system default web browser pointing to {@code url}
	 * 
	 * @param app The parent Application object
	 * @param url The URL to visit.
	 */
	public static void launchBrowser(Application app, String url)
	{
		app.getHostServices().showDocument(url);
	}
}