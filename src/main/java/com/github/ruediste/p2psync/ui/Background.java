package com.github.ruediste.p2psync.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

/**
 * Runs blocking work off the Swing event dispatch thread and delivers the
 * result back on it. UI code must not perform file, network, or persistence
 * work directly in action listeners.
 */
final class Background {
    private Background() {
    }

    static <T> void run(String description, Callable<T> work, Consumer<T> onSuccess,
            Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null)
                        onSuccess.accept(result);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } catch (CancellationException ignored) {
                } catch (ExecutionException error) {
                    if (onError != null)
                        onError.accept(error.getCause() == null ? error : error.getCause());
                }
            }
        }.execute();
    }

    static void run(String description, Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        run(description, () -> {
            work.run();
            return null;
        }, ignored -> {
            if (onSuccess != null)
                onSuccess.run();
        }, onError);
    }
}
