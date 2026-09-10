param(
    [string]$OutputPath = (Join-Path $PWD 'companheiro_crash.txt')
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path ($env:ANDROID_HOME ?? (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb.exe não encontrado: $adb" }
if ((& $adb get-state).Trim() -ne 'device') { throw 'Conecte e autorize o celular por USB antes de executar.' }

& $adb logcat -c
& $adb shell am force-stop br.com.companheirofala
& $adb shell monkey -p br.com.companheirofala 1
Write-Host 'No celular, toque em TESTAR IA LOCAL ou fale com a Gabi. Aguarde o travamento ou a resposta e pressione Enter aqui.'
[void](Read-Host)
& $adb logcat -d | Select-String -Pattern 'CompanheiroLLM|FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|llama|ggml|JNI|OutOfMemory|dlopen|tombstone|DEBUG|libc' | Set-Content -Encoding utf8 $OutputPath
Write-Host "Log salvo em: $OutputPath"
