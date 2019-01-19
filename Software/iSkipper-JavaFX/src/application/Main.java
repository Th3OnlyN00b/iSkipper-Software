package application;

import java.io.IOException;
import java.lang.reflect.Field;

import com.csr460.iSkipper.emulator.Emulator;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDecorator;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import views.PrimaryViewController;
import views.SelectPortsViewController;

public class Main extends Application
{
	private Stage stage;
	private JFXDecorator decorator;
	private Pane selectPortsPane;
	private Scene scene;
	private Pane primaryViewPane;

	private static final double STAGE_MIN_HEIGHT_PADDING = 50.0;
	private static final double STAGE_MIN_WIDTH_PADDING = 10.0;

	@Override
	public void start(Stage primaryStage)
	{
		stage = primaryStage;
		try
		{
			loadSelectPortsView();
			initializeDecorator(selectPortsPane);
			initializeSelectPortsScene();
			initializeStage(scene, false);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void initializeStage(Scene scene, boolean isResizable)
	{
		stage.setScene(scene);
		stage.setTitle("i>Skipper");
		stage.setResizable(isResizable);
		stage.getIcons().add(new Image(this.getClass().getResourceAsStream("/resource/icon.png")));
		stage.setOnCloseRequest(e ->
		{
			Platform.exit();
			System.exit(0);
		});
		stage.show();
	}

	private void initializeSelectPortsScene()
	{
		scene = new Scene(decorator);
		scene.getStylesheets().add(this.getClass().getResource("/css/application.css").toExternalForm());
	}

	private void loadSelectPortsView() throws IOException
	{
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(this.getClass().getResource("/views/SelectPortsView.fxml"));
		selectPortsPane = loader.load();
		SelectPortsViewController controller = loader.getController();
		controller.setMainClass(this);

	}

	private void initializeDecorator(Pane pane)
	{
		decorator = new JFXDecorator(stage, pane);
		try
		{ // Hide the full screen button through reflection.
			Field fullScreenButtonField = JFXDecorator.class.getDeclaredField("btnFull");
			fullScreenButtonField.setAccessible(true);
			JFXButton fullScreenButton = (JFXButton) fullScreenButtonField.get(decorator);
			fullScreenButton.setDisable(true);
			fullScreenButton.setVisible(false);
			// Disable the maximize button
			setMaximizeButoonEnable(false);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		// Set the close button to exit the whole program
		decorator.setOnCloseButtonAction(() ->
		{
			Platform.exit();
			System.exit(0);
		});
	}

	private void setMaximizeButoonEnable(boolean isEnable)
	{
		try
		{
			Field maxButtonField = JFXDecorator.class.getDeclaredField("btnMax");
			maxButtonField.setAccessible(true);
			JFXButton maxButton = (JFXButton) maxButtonField.get(decorator);
			maxButton.setDisable(!isEnable);;
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void showPrimaryView(Emulator emulator)
	{
		if (emulator == null)
			throw new NullPointerException("Emulator cannot be null!");
		stage.close();
		FXMLLoader loader = new FXMLLoader();
		loader.setLocation(this.getClass().getResource("/views/PrimaryView.fxml"));
		PrimaryViewController controller = new PrimaryViewController();
		controller.setEmulator(emulator);
		controller.setApplication(this);
		loader.setController(controller);
		try
		{
			primaryViewPane = loader.load();
		} catch (IOException e)
		{
			e.printStackTrace();
			return;
		}
		decorator.setContent(primaryViewPane);
		setMaximizeButoonEnable(true);
		stage.show();
		stage.setMinHeight(primaryViewPane.getMinHeight() + STAGE_MIN_HEIGHT_PADDING);
		stage.setMinWidth(primaryViewPane.getMinWidth() + STAGE_MIN_WIDTH_PADDING);
		stage.setResizable(true);
	}

	public static void main(String[] args)
	{
		launch(args);
	}
}
