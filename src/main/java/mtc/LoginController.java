package mtc;

import java.util.function.BiPredicate;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Basic login form implementation
 * 
 * @author Fastily
 *
 */
public class LoginController
{
	/**
	 * The location of this controller's FXML.
	 */
	public static final String fxmlLoc = "Login.fxml";

	/**
	 * The method to run on a successful login. PRECONDITION: This must be set before using.
	 */
	private Runnable callback;

	/**
	 * Function used to verify credentials. Called by every login attempt. First parameter is username, second is password. Return true to proceed.
	 */
	private BiPredicate<String, String> credTest;

	/**
	 * The username text field
	 */
	@FXML
	protected TextField userF;

	/**
	 * The password text field
	 */
	@FXML
	protected PasswordField pxF;

	/**
	 * The login Button
	 */
	@FXML
	protected Button loginButton;

	/**
	 * Initializes data fields with the specified values. CAVEAT: This MUST be called before displaying the Login screen.
	 * 
	 * @param credTest Verifies credentials. Called by every login attempt. First parameter is username, second is password. Return true to proceed.
	 * @param callback Executed on the FXML thread on successful login.
	 */
	public void initData(BiPredicate<String, String> credTest, Runnable callback)
	{
		this.credTest = credTest;
		this.callback = callback;
	}

	/**
	 * Attempts login, instantiates this object's Wiki object on success. Shows error message on failure.
	 * 
	 * @param e The ActionEvent that triggered this method call1
	 */
	@FXML
	protected void tryLogin(ActionEvent e)
	{
		String user = userF.getText().trim(), px = pxF.getText();

		if (user.isEmpty() || px.isEmpty())
			FXTool.alertUser("Username/Password cannot be empty!", AlertType.INFORMATION);
		else
			new Thread(new LoginTask(user, px)).start();
	}

	/**
	 * Represents a login attempt.
	 * 
	 * @author Fastily
	 *
	 */
	private class LoginTask extends Task<Boolean>
	{
		/**
		 * The username and password to use
		 */
		private String user, px;

		/**
		 * Constructor, creates a new LoginTask
		 * 
		 * @param user The username to login with
		 * @param px The password to login with
		 */
		private LoginTask(String user, String px)
		{
			this.user = user;
			this.px = px;

			loginButton.disableProperty().bind(runningProperty());
		}

		/**
		 * Attempt login
		 */
		public Boolean call()
		{
			return credTest.test(user, px);
		}

		/**
		 * Displays error on login fail, or closes this window and run {@code callback}
		 */
		public void succeeded()
		{
			if (!getValue())
				FXTool.alertUser("Could not login. Please try again and/or verify that you are connected to the internet.", Alert.AlertType.ERROR);
			else
			{
				((Stage) loginButton.getScene().getWindow()).close();
				Platform.runLater(callback);
			}
		}
	}
}