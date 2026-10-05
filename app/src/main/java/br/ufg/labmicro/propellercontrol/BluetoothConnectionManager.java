package br.ufg.labmicro.propellercontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the RFCOMM socket and serializes every connection/write operation. */
public final class BluetoothConnectionManager implements AutoCloseable {
    public enum State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    public interface Listener {
        void onStateChanged(State state, String detail);
        void onBytesSent(String description, byte[] data);
    }

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter adapter;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private volatile BluetoothSocket socket;
    private volatile OutputStream output;
    private volatile State state = State.DISCONNECTED;

    public BluetoothConnectionManager(BluetoothAdapter adapter, Listener listener) {
        this.adapter = adapter;
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        BluetoothSocket current = socket;
        return state == State.CONNECTED && current != null && current.isConnected() && output != null;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device, String displayName) {
        if (state == State.CONNECTING) return;
        publishState(State.CONNECTING, "Conectando a " + displayName + "...");
        ioExecutor.execute(() -> {
            closeResources();
            try {
                if (adapter != null) adapter.cancelDiscovery();
                BluetoothSocket candidate = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket = candidate;
                candidate.connect();
                output = candidate.getOutputStream();
                publishState(State.CONNECTED, "Conectado a " + displayName);
            } catch (Exception exception) {
                closeResources();
                publishState(State.ERROR, readableError(exception));
            }
        });
    }

    public void send(byte[] bytes, String description) {
        final byte[] safeCopy = Arrays.copyOf(bytes, bytes.length);
        ioExecutor.execute(() -> {
            try {
                OutputStream currentOutput = output;
                if (!isConnected() || currentOutput == null) throw new IOException("Bluetooth desconectado");
                currentOutput.write(safeCopy);
                currentOutput.flush();
                mainHandler.post(() -> listener.onBytesSent(description, safeCopy));
            } catch (Exception exception) {
                closeResources();
                publishState(State.ERROR, readableError(exception));
            }
        });
    }

    public void disconnect() {
        closeResources(); // Closing here also interrupts a blocking connect/write.
        ioExecutor.execute(() -> publishState(State.DISCONNECTED, "Bluetooth desconectado"));
    }

    private void publishState(State newState, String detail) {
        state = newState;
        mainHandler.post(() -> listener.onStateChanged(newState, detail));
    }

    private String readableError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    private synchronized void closeResources() {
        OutputStream currentOutput = output;
        BluetoothSocket currentSocket = socket;
        output = null;
        socket = null;
        if (currentOutput != null) {
            try { currentOutput.close(); } catch (IOException ignored) { }
        }
        if (currentSocket != null) {
            try { currentSocket.close(); } catch (IOException ignored) { }
        }
    }

    @Override
    public void close() {
        closeResources();
        state = State.DISCONNECTED;
        ioExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
