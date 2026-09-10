param(
    [string]$DeviceModelPath = "/sdcard/Download/model.gguf",
    [string]$Prompt = "Responda em português do Brasil, em uma frase curta: qual animal faz miau?"
)

$ErrorActionPreference = 'Stop'
$package = 'br.com.companheirofala'
$target = 'files/models/model.gguf'
$adb = Join-Path ($env:ANDROID_HOME ?? (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb.exe não encontrado: $adb" }

if ((& $adb get-state).Trim() -ne 'device') { throw 'Nenhum dispositivo ADB autorizado.' }
& $adb shell "test -s '$DeviceModelPath'"
if ($LASTEXITCODE -ne 0) { throw "GGUF ausente ou vazio: $DeviceModelPath" }

# O shell lê Download e transmite os bytes ao processo run-as, que grava no armazenamento privado.
& $adb shell "run-as $package mkdir -p files/models; cat '$DeviceModelPath' | run-as $package sh -c 'cat > $target'"
if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar o GGUF para files/models.' }

& $adb shell "run-as $package test -s $target"
if ($LASTEXITCODE -ne 0) { throw 'O GGUF não foi encontrado no armazenamento privado após a cópia.' }

& $adb logcat -c
& $adb shell am broadcast -a br.com.companheirofala.action.TEST_LOCAL_LLAMA --es prompt "$Prompt"
Write-Host "Acompanhe o resultado real com: & '$adb' logcat -s LocalLlmAdbTest:L llama-android:L *:S"
