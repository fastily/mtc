package mtc;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.util.FSystem;
import fastily.wpkit.ui.FXTool;
import fastily.wpkit.ui.LoginController;
import fastily.wpkit.util.Toolbox;

/**
 * A GUI wrapper for MTC
 * 
 * @author Fastily
 */
public class App extends Application
{
	/**
	 * The Wiki object to use.
	 */
	private Wiki wiki = new Wiki("en.wikipedia.org");
		
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
		String minVersion =  wiki.getPageText(MStrings.fullname + "/Version").trim();
		if(!Toolbox.versionCheck(MStrings.version, minVersion))
		{
			FXTool.warnUser(String.format("Your version of %s (%s) is outdated.  The current version is (%s), please download the newest version.", MStrings.name, MStrings.version, minVersion)); 
			FXTool.launchBrowser(this, MStrings.enwpURLBase + MStrings.fullname);

			Platform.exit();
		}
		
		// Start Login Window
		FXMLLoader lcLoader = FXTool.makeNewLoader(LoginController.fxmlLoc, LoginController.class);
		stage.setScene(new Scene(lcLoader.load()));
		
		lcLoader.<LoginController>getController().initData(wiki, this::createAndShowMTC);
				
		stage.setTitle(MStrings.name);
		stage.show();
	}
		
	/**
	 * Creates and shows the main MTC UI.  Also checks the minimum allowed version.
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
      catch(Throwable e)
      {
      	FSystem.errAndExit(e, "Should never reach here, is your FXML malformed or missing?");
      }
      
      lcLoader.<MTCController>getController().initData(wiki);
   
      stage.setTitle(MStrings.name);
      stage.show();
	}
}