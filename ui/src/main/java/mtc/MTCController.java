package mtc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import mtc.MTC.TransferFile;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.util.FL;

/**
 * An MTC UI window
 * 
 * @author Fastily
 *
 */
public class MTCController
{
	/**
	 * The location of this controller's FXML.
	 */
	public static final String fxmlLoc = "MTC.fxml";

	/**
	 * Date format for prefixing output.
	 */
	private static DateTimeFormatter df = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a");

	@FXML
	protected TextArea console;

	/**
	 * The ProgressBar for the UI
	 */
	@FXML
	protected ProgressBar pb;

	/**
	 * The ComboBox for mode selection
	 */
	@FXML
	protected ComboBox<TransferMode> modeSelect;

	/**
	 * The TextField for user input
	 */
	@FXML
	protected TextField textInput;

	/**
	 * Input field for category
	 */
	@FXML
	protected TextField catInput;
	
	/**
	 * UI component toggling the smart filter
	 */
	@FXML
	protected CheckMenuItem filterToggle;

	/**
	 * UI component toggling the post-transfer delete function
	 */
	@FXML
	protected CheckMenuItem deleteToggle;

	/**
	 * UI component toggling the addition of a maintenance category.
	 */
	@FXML
	protected CheckMenuItem maintToggle;
	
	/**
	 * The exit button in the menu
	 */
	@FXML
	protected MenuItem menuItemExit;

	/**
	 * The start Button
	 */
	@FXML
	protected Button startButton;

	/**
	 * The Wiki objects to use with MTC.
	 */
	private Wiki enwp;

	/**
	 * The MTC instance for this Controller.
	 */
	private MTC mtc;

	/**
	 * The most recently created TransferTask. This may or may not be running.
	 */
	private TransferTask currTask = null;

	/**
	 * Performs simple UI initialization using {@code wiki}. CAVEAT: This must be called before attempting to display the
	 * MTC window.
	 * 
	 * @param enwp The Wiki for this controller to use.
	 */
	protected void initData(MTC mtc)
	{
		enwp = (this.mtc = mtc).enwp;

		printToConsole(String.format("Hello %s, welcome to MTC!%n", enwp.whoami()));
		modeSelect.getItems().addAll(TransferMode.values());
		deleteToggle.setDisable(!enwp.listUserRights(enwp.whoami()).contains("sysop"));
	}

	/**
	 * Exits the application
	 */
	@FXML
	protected void onExitButtonPress()
	{
		Platform.exit();
	}

	/**
	 * Transfers files to Commons as per user input.
	 */
	@FXML
	protected void onStartButtonClick()
	{
		if (currTask == null || currTask.isDone())
		{
			String text = textInput.getText().trim();
			TransferMode mode = modeSelect.getSelectionModel().getSelectedItem();

			if (text.isEmpty() || mode == null)
				FXTool.warnUser("Please select a transfer mode and specify a File, Category, Username, or Template to continue.");
			else
				new Thread(currTask = new TransferTask(mode, text)).start();
		}
		else if (currTask.isRunning())
			currTask.cancel(false);
	}

	/**
	 * Adds a time-stamped message to the {@code console} TextArea.
	 * 
	 * @param msg The new message to add.
	 */
	private synchronized void printToConsole(String msg)
	{
		console.appendText(String.format("(%s): %s%n", LocalDateTime.now().format(df), msg));
	}

	/**
	 * Business logic for transferring a set of file(s) to Commons.
	 * 
	 * @author Fastily
	 *
	 */
	private class TransferTask extends Task<Void>
	{
		/**
		 * The mode of transfer to attempt.
		 */
		private TransferMode mode;

		/**
		 * The text collected from user input.
		 */
		private String userInput;

		/**
		 * Titles of all files which could not be transferred.
		 */
		private ArrayList<String> fails = new ArrayList<>();

		/**
		 * Constructor, creates a new TransferTask.
		 * 
		 * @param mode The TransferMode to use.
		 * @param userInput The text input from the user.
		 */
		private TransferTask(TransferMode mode, String userInput)
		{
			this.mode = mode;
			this.userInput = userInput;

			mtc.ignoreFilter = filterToggle.isSelected();
			mtc.deleteOnTransfer = deleteToggle.isSelected();
			mtc.useCheckNeededCat = maintToggle.isSelected();

			messageProperty().addListener((obv, o, n) -> printToConsole(n));
			stateProperty().addListener((obv, o, n) -> {
				switch (n)
				{
					case SCHEDULED:
						console.clear();
						startButton.setText("Cancel");
						updateProgress(0, 1);
						break;
					case CANCELLED:
					case SUCCEEDED:
					case FAILED:
						startButton.setText("Start");
						updateProgress(1, 1);
						break;

					default:
						break;
				}
			});

			setOnCancelled(e -> updateMessage("You cancelled this transfer!"));
			setOnFailed(e -> updateMessage("Something's not right."));
			setOnSucceeded(e -> updateMessage(String.format("Task succeeded, with %d failures: %s", fails.size(), fails)));

			pb.progressProperty().bind(progressProperty());
		}

		/**
		 * Performs the actual file transfer(s).
		 */
		public Void call()
		{
			updateMessage("Please wait, querying server...");

			ArrayList<String> fl;
			switch (mode)
			{
				case FILE:
					fl = FL.toSAL(enwp.convertIfNotInNS(userInput, NS.FILE));
					break;
				case CATEGORY:
					fl = enwp.getCategoryMembers(enwp.convertIfNotInNS(userInput, NS.CATEGORY), NS.FILE);
					break;
				case USER:
					fl = enwp.getUserUploads(enwp.nss(userInput));
					break;
				case TEMPLATE:
					fl = enwp.whatTranscludesHere(enwp.convertIfNotInNS(userInput, NS.TEMPLATE), NS.FILE);
					break;
				case FILELINKS:
					fl = enwp.getImagesOnPage(userInput);
					break;
				case LINKS:
					fl = enwp.getLinksOnPage(true, userInput, NS.FILE);
					break;
				default:
					fl = new ArrayList<>();
					break;
			}

			ArrayList<TransferFile> tol = mtc.makeTransferFile(fl);
			int tolSize = tol.size();

			// Checkpoint - kill Task now if cancelled
			if (isCancelled())
				return null;

			// Apply Category information
			if(!catInput.getText().trim().isEmpty())
			{
				String[] catL = catInput.getText().trim().split("\\|");
				for(TransferFile t : tol)
					t.addCat(catL);
			}
			
			updateMessage(String.format("[Total/Filtered/Eligible]: [%d/%d/%d]", fl.size(), fl.size() - tolSize, tolSize));

			if (tol.isEmpty())
			{
				updateMessage("Found no file(s) matching your request; verify your input(s) and/or disable the smart filter.");
				updateProgress(0, 1);
			}
			else
				for (int i = 0; i < tol.size() && !isCancelled(); i++)
				{
					TransferFile to = tol.get(i);

					updateProgress(i, tolSize);
					updateMessage(String.format("Transfer [%d/%d]: %s", i, tolSize, to.wpFN));

					if (!to.doTransfer())
						fails.add(to.wpFN);
				}

			return null;
		}
	}

	/**
	 * Represents various supported file transfer modes.
	 * 
	 * @author Fastily
	 *
	 */
	private enum TransferMode
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
}