package com.chrionline.ui;

import com.chrionline.client.Client;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Handles client-side automatic logout after a period of user inactivity.
 */
public final class SessionTimeoutManager {

    private static final Duration INACTIVITY_TIMEOUT = Duration.minutes(10);

    private static final PauseTransition inactivityTimer = new PauseTransition(INACTIVITY_TIMEOUT);
    private static final EventHandler<Event> activityHandler = event -> resetTimer();

    private static Scene attachedScene;

    static {
        inactivityTimer.setOnFinished(event -> {
            if (!ClientSession.getInstance().isLoggedIn()) {
                return;
            }
            Client.getInstance().disconnect();
            ClientSession.getInstance().clear();
            ErrorHandler.showWarningDialog("Session expiree", "Deconnexion automatique apres 10 minutes d'inactivite.");
            Platform.runLater(SceneManager::showLogin);
        });
    }

    private SessionTimeoutManager() {}

    public static void attach(Scene scene) {
        if (attachedScene == scene) {
            resetTimer();
            return;
        }

        detachCurrentScene();
        attachedScene = scene;
        if (scene == null) {
            inactivityTimer.stop();
            return;
        }

        scene.addEventFilter(MouseEvent.ANY, activityHandler);
        scene.addEventFilter(KeyEvent.ANY, activityHandler);
        resetTimer();
    }

    public static void resetTimer() {
        if (!ClientSession.getInstance().isLoggedIn()) {
            inactivityTimer.stop();
            return;
        }
        inactivityTimer.playFromStart();
    }

    public static void stop() {
        inactivityTimer.stop();
    }

    private static void detachCurrentScene() {
        if (attachedScene == null) {
            return;
        }
        attachedScene.removeEventFilter(MouseEvent.ANY, activityHandler);
        attachedScene.removeEventFilter(KeyEvent.ANY, activityHandler);
        attachedScene = null;
    }
}
