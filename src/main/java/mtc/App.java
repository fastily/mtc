package mtc;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import fastily.jwiki.util.FSystem;

/**
 * A GUI wrapper for MTC
 * 
 * @author Fastily
 */
public class App extends Application
{	
	/**
	 * The MTC object to use.
	 */
	private MTC mtc = new MTC();

	/**
	 * Main Driver
	 * 
	 * @param args Program args, not used.
	 */
	public static void main(String[] args)
	{
		launch(args);
	}

	/**
	 * Called when MTC first starts.
	 */
	public void start(Stage stage) throws Exception
	{
		// Check Version
		String minVersion = mtc.enwp.getPageText(MStrings.fullname + "/Version").trim();
		if (!versionCheck(MStrings.version, minVersion))
		{
			FXTool.warnUser(
					String.format("Your version of %s (%s) is outdated.  The current version is (%s), please download the newest version.",
							MStrings.name, MStrings.version, minVersion));
			FXTool.launchBrowser(this, "https://en.wikipedia.org/wiki/" + MStrings.fullname);

			Platform.exit();
		}

		// Start Login Window
		FXMLLoader lcLoader = FXTool.makeNewLoader(LoginController.fxmlLoc, LoginController.class);
		stage.setScene(new Scene(lcLoader.load()));

		lcLoader.<LoginController> getController().initData(mtc::login, this::createAndShowMTC);

		stage.setTitle(MStrings.name);
		stage.show();
	}

	/**
	 * Creates and shows the main MTC UI. Also checks the minimum allowed version.
	 * 
	 * @param wiki The Wiki object to use with the UI
	 */
	private void createAndShowMTC()
	{
		FXMLLoader lcLoader = FXTool.makeNewLoader(MTCController.fxmlLoc, MTCController.class);

		Stage stage = new Stage();
		try
		{
			stage.setScene(new Scene(lcLoader.load()));
		}
		catch (Throwable e)
		{
			FSystem.errAndExit(e, "Should never reach here, is your FXML malformed or missing?");
		}

		lcLoader.<MTCController> getController().initData(mtc);

		stage.setTitle(MStrings.name);
		stage.show();
	}

	/**
	 * Checks the version String of a program with the version String of the server. PRECONDITION: {@code local} and
	 * {@code minVersion} ONLY contain numbers and the '.' character.
	 * 
	 * @param local The version String of the program. (e.g. 0.2.1)
	 * @param minVersion The version String of the server. (e.g. 1.3.2)
	 * @return True if the version of the local String is greater than or equal to the server's version String.
	 */
	public static boolean versionCheck(String local, String minVersion)
	{
		try
		{
			return Integer.parseInt(local.replace(".", "")) >= Integer.parseInt(minVersion.replace(".", ""));
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
		return false;
	}
}