# Builds the plugin and launches a RuneLite dev client with it loaded.
# Requires JDK 11 (Gradle 6.6.1 here can't run under newer JDKs) - point $JdkHome
# at a JDK 11 install, or let this script fall back to one already unpacked at
# %USERPROFILE%\.jdks.
# Usage: powershell -ExecutionPolicy Bypass -File .\run-dev-client.ps1

$ErrorActionPreference = "Stop"

$JdkHome = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -Filter "jdk-11*" |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $JdkHome) {
    throw "No JDK 11 found under $env:USERPROFILE\.jdks. Download Temurin 11 and extract it there, or edit `$JdkHome in this script."
}

$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"

$initScript = Join-Path $env:TEMP "improvedexchangelogger-classpath-init.gradle"
@'
gradle.allprojects {
    task printTestRuntimeClasspath {
        doLast {
            println("CLASSPATH_START")
            println(sourceSets.test.runtimeClasspath.join(File.pathSeparator))
            println("CLASSPATH_END")
        }
    }
}
'@ | Set-Content -Path $initScript -Encoding ascii

& .\gradlew.bat testClasses --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$cpOutput = & .\gradlew.bat --init-script $initScript -q printTestRuntimeClasspath
if ($LASTEXITCODE -ne 0) { throw "Gradle classpath resolution failed" }
$startIndex = [array]::IndexOf($cpOutput, "CLASSPATH_START")
if ($startIndex -lt 0) { throw "Could not find classpath in Gradle output" }
$classpath = $cpOutput[$startIndex + 1]

Write-Host "Launching RuneLite dev client with Exchange Logger loaded..."
& "$JdkHome\bin\java.exe" -ea -cp $classpath com.improvedexchangelogger.ImprovedExchangeLoggerPluginTest
