# Propeller HC-06 Android

Aplicativo Android nativo para controlar um firmware 8051 por Bluetooth clássico SPP/RFCOMM usando o módulo HC-06.

## Funções

- Lista somente dispositivos já pareados e seleciona `HC-06` automaticamente quando encontrado.
- Conecta por RFCOMM com o UUID SPP `00001101-0000-1000-8000-00805F9B34FB`.
- Envia texto de até 16 bytes, validado conforme a tabela de caracteres do firmware.
- Converte `→`, `←`, `☺` e `█` para `7E`, `7F`, `93` e `FF`.
- Envia os comandos binários `E0 2F` (mensagem padrão) e `E1 2F` (RPM).
- Mantém um relógio ao vivo no formato `DD-MM HH:MM:SS/`, alinhado à mudança do segundo.
- Exibe estado da conexão e log de transmissões.

O caractere `/` é reservado como terminador e não pode aparecer no texto. O app não configura baud rate; o HC-06/UART deve estar configurado para os 9600 baud esperados pelo firmware.

## Compilar

Requisitos: JDK 17 e Android SDK 31 com Build Tools 30.0.3.

No Windows:

```powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug
```

No Linux/macOS:

```sh
./gradlew clean testDebugUnitTest assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Os testes de protocolo não usam bibliotecas externas: a tarefa `protocolTest` é executada automaticamente antes de `testDebugUnitTest` e valida texto, limite de 16 bytes, comandos E0/E1, relógio, símbolos e rejeição de entradas inválidas.

## Permissões

Em Android 12 ou superior, o app solicita `BLUETOOTH_CONNECT` em runtime. Em versões anteriores usa `BLUETOOTH` e `BLUETOOTH_ADMIN`, limitadas no manifesto ao Android 11. Não é feita varredura e nenhuma permissão de localização é solicitada.

## Observação do firmware

O firmware original pode manter `REN=0` depois dos comandos E0/E1. O app envia exatamente o protocolo especificado e mostra um aviso; ele não tenta contornar o problema com bytes extras.
