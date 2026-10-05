package br.ufg.labmicro.propellercontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity implements BluetoothConnectionManager.Listener {
    private static final int REQUEST_BLUETOOTH_CONNECT = 1001;
    private static final int MAX_LOG_LINES = 120;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private final List<String> pairedLabels = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothConnectionManager connectionManager;
    private Spinner deviceSpinner;
    private Button connectButton;
    private Button disconnectButton;
    private TextView statusView;
    private EditText messageEdit;
    private TextView counterView;
    private Button sendTextButton;
    private TextView clockPreview;
    private Button clockButton;
    private TextView logView;
    private boolean clockRunning;

    private final Runnable previewRunnable = new Runnable() {
        @Override public void run() {
            updateClockPreview();
            mainHandler.postDelayed(this, delayToNextSecond());
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (!clockRunning) return;
            if (!connectionManager.isConnected()) {
                stopLiveClock("Relogio parado: Bluetooth desconectado.");
                return;
            }
            String now = formattedNow();
            connectionManager.send(DisplayProtocol.textPacket(now), "relogio: " + now);
            mainHandler.postDelayed(this, delayToNextSecond());
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        connectionManager = new BluetoothConnectionManager(bluetoothAdapter, this);
        buildUi();
        mainHandler.post(previewRunnable);
        ensurePermissionAndRefresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 246, 248));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, matchWrap());

        TextView title = text("Propeller HC-06", 24, Color.rgb(31, 41, 55));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("Controle Bluetooth classico SPP para o 8051", 14, Color.rgb(95, 107, 122));
        subtitle.setPadding(0, dp(3), 0, dp(5));
        root.addView(subtitle);

        LinearLayout bluetoothCard = card(root, "Bluetooth");
        deviceSpinner = new Spinner(this);
        bluetoothCard.addView(deviceSpinner, matchWrap());

        LinearLayout refreshRow = row();
        bluetoothCard.addView(refreshRow, matchWrapTop());
        Button refreshButton = button("Atualizar pareados");
        refreshButton.setOnClickListener(v -> ensurePermissionAndRefresh());
        refreshRow.addView(refreshButton, weighted());
        Button settingsButton = button("Ativar / parear");
        settingsButton.setOnClickListener(v -> openBluetoothSettings());
        refreshRow.addView(settingsButton, weightedLeft());

        LinearLayout connectionRow = row();
        bluetoothCard.addView(connectionRow, matchWrapTop());
        connectButton = primaryButton("Conectar");
        connectButton.setOnClickListener(v -> connectSelectedDevice());
        connectionRow.addView(connectButton, weighted());
        disconnectButton = button("Desconectar");
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(v -> {
            stopLiveClock(null);
            connectionManager.disconnect();
        });
        connectionRow.addView(disconnectButton, weightedLeft());

        statusView = text("Desconectado", 14, Color.rgb(179, 38, 30));
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setPadding(0, dp(10), 0, 0);
        bluetoothCard.addView(statusView);

        LinearLayout customCard = card(root, "Mensagem personalizada");
        LinearLayout editRow = row();
        customCard.addView(editRow, matchWrap());
        messageEdit = new EditText(this);
        messageEdit.setHint("Ate 16 caracteres suportados");
        messageEdit.setSingleLine(true);
        messageEdit.setTextSize(17);
        messageEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        editRow.addView(messageEdit, weighted());
        counterView = text("0 / 16", 13, Color.rgb(95, 107, 122));
        counterView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams counterParams = new LinearLayout.LayoutParams(dp(66), dp(52));
        counterParams.leftMargin = dp(8);
        editRow.addView(counterView, counterParams);
        messageEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validateMessage(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout symbolsRow = row();
        customCard.addView(symbolsRow, matchWrapTop());
        addSymbolButton(symbolsRow, "\u2192");
        addSymbolButton(symbolsRow, "\u2190");
        addSymbolButton(symbolsRow, "\u263A");
        addSymbolButton(symbolsRow, "\u2588");

        sendTextButton = primaryButton("Enviar");
        sendTextButton.setEnabled(false);
        sendTextButton.setOnClickListener(v -> sendCustomText());
        customCard.addView(sendTextButton, matchWrapTop());
        TextView terminatorHelp = smallText("A barra / e reservada. O terminador 2F e acrescentado automaticamente quando o texto tem menos de 16 bytes.");
        terminatorHelp.setPadding(0, dp(8), 0, 0);
        customCard.addView(terminatorHelp);

        LinearLayout commandsCard = card(root, "Comandos do firmware");
        LinearLayout commandsRow = row();
        commandsCard.addView(commandsRow, matchWrap());
        Button defaultButton = button("Mensagem padrao");
        defaultButton.setOnClickListener(v -> sendSpecial(DisplayProtocol.defaultMessageCommand(), "mensagem padrao"));
        commandsRow.addView(defaultButton, weighted());
        Button rpmButton = button("RPM");
        rpmButton.setOnClickListener(v -> sendSpecial(DisplayProtocol.rpmCommand(), "RPM"));
        commandsRow.addView(rpmButton, weightedLeft());
        TextView warning = smallText("Aviso: no firmware original, E0/E1 podem desabilitar novas recepcoes ate o 8051 ser resetado.");
        warning.setTextColor(Color.rgb(155, 93, 0));
        warning.setPadding(0, dp(8), 0, 0);
        commandsCard.addView(warning);

        LinearLayout clockCard = card(root, "Relogio ao vivo");
        clockPreview = text("", 27, Color.rgb(21, 101, 192));
        clockPreview.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockPreview.setGravity(Gravity.CENTER);
        clockPreview.setPadding(0, dp(6), 0, dp(8));
        clockCard.addView(clockPreview, matchWrap());
        clockCard.addView(smallText("Envia DD-MM HH:MM:SS/ uma vez por segundo, alinhado a mudanca do segundo."));
        clockButton = primaryButton("Iniciar relogio");
        clockButton.setOnClickListener(v -> toggleLiveClock());
        clockCard.addView(clockButton, matchWrapTop());

        LinearLayout logCard = card(root, "Log");
        Button clearLogButton = button("Limpar log");
        clearLogButton.setOnClickListener(v -> { logLines.clear(); renderLog(); });
        logCard.addView(clearLogButton, matchWrap());
        logView = text("", 12, Color.rgb(45, 55, 72));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, 0);
        logCard.addView(logView, matchWrap());

        setContentView(scroll);
        appendLog("Pronto. Pareie o HC-06 e atualize a lista.");
    }

    private void ensurePermissionAndRefresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
        } else refreshPairedDevices();
    }

    @SuppressLint("MissingPermission")
    private void refreshPairedDevices() {
        if (bluetoothAdapter == null) {
            setUiStatus(BluetoothConnectionManager.State.ERROR, "Bluetooth indisponivel neste aparelho");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setUiStatus(BluetoothConnectionManager.State.ERROR, "Bluetooth desligado");
            toast("Ative o Bluetooth para listar os dispositivos pareados.");
            return;
        }
        pairedDevices.clear();
        pairedLabels.clear();
        int hc06Index = -1;
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) {
            for (BluetoothDevice device : bonded) {
                String name = safeDeviceName(device);
                if (hc06Index < 0 && "HC-06".equalsIgnoreCase(name.trim())) hc06Index = pairedDevices.size();
                pairedDevices.add(device);
                pairedLabels.add(name + "  -  " + device.getAddress());
            }
        }
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, pairedLabels));
        if (hc06Index >= 0) deviceSpinner.setSelection(hc06Index);
        appendLog(pairedDevices.size() + " dispositivo(s) pareado(s) encontrado(s)." +
                (hc06Index >= 0 ? " HC-06 selecionado." : ""));
        if (pairedDevices.isEmpty()) toast("Nenhum dispositivo pareado. Pareie o HC-06 nas configuracoes.");
    }

    @SuppressLint("MissingPermission")
    private void connectSelectedDevice() {
        if (!hasConnectPermission()) {
            ensurePermissionAndRefresh();
            return;
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            toast("Ative o Bluetooth primeiro.");
            return;
        }
        int position = deviceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= pairedDevices.size()) {
            toast("Escolha um dispositivo pareado.");
            return;
        }
        BluetoothDevice device = pairedDevices.get(position);
        String name = safeDeviceName(device);
        appendLog("Conectando a " + name + "...");
        connectionManager.connect(device, name);
    }

    private void sendCustomText() {
        try {
            String value = messageEdit.getText().toString();
            if (value.isEmpty()) throw new IllegalArgumentException("Digite uma mensagem.");
            sendPacket(DisplayProtocol.textPacket(value), "texto");
        } catch (IllegalArgumentException exception) { toast(exception.getMessage()); }
    }

    private void sendSpecial(byte[] command, String description) {
        stopLiveClock(null);
        sendPacket(command, description);
        appendLog("Aviso: " + description + " pode exigir reset do firmware antes do proximo comando.");
    }

    private void sendPacket(byte[] packet, String description) {
        if (!connectionManager.isConnected()) {
            toast("Conecte ao HC-06 primeiro.");
            return;
        }
        connectionManager.send(packet, description);
    }

    private void validateMessage() {
        String value = messageEdit.getText().toString();
        try {
            int byteCount = DisplayProtocol.encodeContent(value).length;
            counterView.setText(byteCount + " / 16");
            counterView.setTextColor(Color.rgb(95, 107, 122));
            messageEdit.setError(null);
            sendTextButton.setEnabled(byteCount > 0);
        } catch (IllegalArgumentException exception) {
            counterView.setText("! / 16");
            counterView.setTextColor(Color.rgb(179, 38, 30));
            messageEdit.setError(exception.getMessage());
            sendTextButton.setEnabled(false);
        }
    }

    private void toggleLiveClock() {
        if (clockRunning) stopLiveClock("Relogio parado.");
        else startLiveClock();
    }

    private void startLiveClock() {
        if (!connectionManager.isConnected()) {
            toast("Conecte ao HC-06 primeiro.");
            return;
        }
        mainHandler.removeCallbacks(clockRunnable);
        clockRunning = true;
        clockButton.setText("Parar relogio");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        appendLog("Relogio iniciado.");
        clockRunnable.run();
    }

    private void stopLiveClock(String logMessage) {
        boolean wasRunning = clockRunning;
        clockRunning = false;
        mainHandler.removeCallbacks(clockRunnable);
        if (clockButton != null) clockButton.setText("Iniciar relogio");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (wasRunning && logMessage != null) appendLog(logMessage);
    }

    @Override public void onStateChanged(BluetoothConnectionManager.State state, String detail) {
        setUiStatus(state, detail);
        appendLog(detail);
        if (state != BluetoothConnectionManager.State.CONNECTED) stopLiveClock(null);
    }

    @Override public void onBytesSent(String description, byte[] data) {
        appendLog("TX " + description + ": " + DisplayProtocol.toHex(data));
    }

    private void setUiStatus(BluetoothConnectionManager.State state, String detail) {
        statusView.setText(stateLabel(state) + (detail == null || detail.isEmpty() ? "" : " - " + detail));
        int color = state == BluetoothConnectionManager.State.CONNECTED ? Color.rgb(0, 121, 107)
                : state == BluetoothConnectionManager.State.CONNECTING ? Color.rgb(21, 101, 192)
                : Color.rgb(179, 38, 30);
        statusView.setTextColor(color);
        boolean busy = state == BluetoothConnectionManager.State.CONNECTING;
        connectButton.setEnabled(!busy && state != BluetoothConnectionManager.State.CONNECTED);
        disconnectButton.setEnabled(state == BluetoothConnectionManager.State.CONNECTED || busy);
    }

    private String stateLabel(BluetoothConnectionManager.State state) {
        switch (state) {
            case CONNECTING: return "Conectando";
            case CONNECTED: return "Conectado";
            case ERROR: return "Erro";
            default: return "Desconectado";
        }
    }

    private void openBluetoothSettings() { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "Dispositivo Bluetooth" : name;
        } catch (SecurityException exception) { return "Dispositivo Bluetooth"; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLUETOOTH_CONNECT) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) refreshPairedDevices();
        else {
            setUiStatus(BluetoothConnectionManager.State.ERROR, "Permissao de dispositivos proximos negada");
            toast("A permissao de dispositivos proximos e necessaria para conectar ao HC-06.");
        }
    }

    @Override protected void onDestroy() {
        stopLiveClock(null);
        mainHandler.removeCallbacksAndMessages(null);
        connectionManager.close();
        super.onDestroy();
    }

    private String formattedNow() {
        return new SimpleDateFormat("dd-MM HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private void updateClockPreview() { if (clockPreview != null) clockPreview.setText(formattedNow()); }

    private long delayToNextSecond() {
        long remainder = System.currentTimeMillis() % 1000L;
        return remainder == 0L ? 1000L : 1000L - remainder;
    }

    private void appendLog(String message) {
        if (logView == null) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logLines.add("[" + time + "] " + message);
        while (logLines.size() > MAX_LOG_LINES) logLines.remove(0);
        renderLog();
    }

    private void renderLog() {
        StringBuilder text = new StringBuilder();
        for (String line : logLines) text.append(line).append('\n');
        logView.setText(text.toString());
    }

    private void addSymbolButton(LinearLayout parent, String symbol) {
        Button button = button(symbol);
        button.setTextSize(21);
        button.setOnClickListener(v -> {
            int start = Math.max(0, messageEdit.getSelectionStart());
            int end = Math.max(0, messageEdit.getSelectionEnd());
            if (messageEdit.length() >= 16 && start == end) return;
            messageEdit.getText().replace(Math.min(start, end), Math.max(start, end), symbol);
        });
        parent.addView(button, weightedLeft());
    }

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(225, 229, 235));
        card.setBackground(background);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        parent.addView(card, params);
        TextView title = text(heading, 17, Color.rgb(31, 41, 55));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(10));
        card.addView(title);
        return card;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(48));
        return button;
    }

    private Button primaryButton(String label) {
        Button button = button(label);
        button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(21, 101, 192));
        background.setCornerRadius(dp(8));
        button.setBackground(background);
        return button;
    }

    private TextView smallText(String value) { return text(value, 13, Color.rgb(95, 107, 122)); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapTop() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedLeft() {
        LinearLayout.LayoutParams params = weighted();
        params.leftMargin = dp(8);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
