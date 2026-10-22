@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem 药速达 JMeter 批次 1：T01 / T10 / T11 / S61 / S62
rem
rem 用法：
rem   1) 直接双击（须先用环境变量提供 JMETER_HOME 或把 jmeter.bat 加进 PATH）
rem   2) 命令行带参数覆盖：run-batch1.bat -Jtoken=eyJ... -JadminToken=eyJ... -Jcid=11 -JdrugId=1
rem
rem 前置：先看 jmeter\README.md 完成 token / cid / drugId 准备
rem 说明：Node/JDK 环境与业务KPI无关；本脚本只负责用非 GUI 模式跑 .jmx 并生成 HTML 报告
rem 注意：-e -o 要求 reports\<name>\ 目录不存在，本脚本每个计划前都会检查
rem ============================================================

cd /d "%~dp0"
set "EXTRA=%*"

set "JMX_DIR=%~dp0plans"
set "RESULT_DIR=%~dp0results"
set "REPORT_DIR=%~dp0reports"

if not exist "%RESULT_DIR%" mkdir "%RESULT_DIR%"
if not exist "%REPORT_DIR%" mkdir "%REPORT_DIR%"

rem ---- 定位 JMeter ----
set "JMETER_BAT="
if defined JMETER_HOME (
  if exist "%JMETER_HOME%\bin\jmeter.bat" set "JMETER_BAT=%JMETER_HOME%\bin\jmeter.bat"
)
if not defined JMETER_BAT (
  for %%J in (jmeter.bat) do (
    if not defined JMETER_BAT (
      where %%J >nul 2>nul
      if !errorlevel! equ 0 set "JMETER_BAT=%%J"
    )
  )
)
if not defined JMETER_BAT (
  echo [ERROR] 找不到 JMeter。请设置 JMETER_HOME 指向 JMeter 根目录，或把 jmeter.bat 加入 PATH。
  exit /b 1
)
echo 使用 JMeter: %JMETER_BAT%
echo 结果目录 : %RESULT_DIR%
echo 报告目录 : %REPORT_DIR%
echo 额外参数 : %EXTRA%

call :run t01-baseline
call :run t10-cache-breakdown
call :run t11-cache-avalanche
call :run s61-feign-fallback
call :run s62-gateway-flow

echo.
echo ===== 批次 1 结束 =====
echo HTML 报告：
echo   %REPORT_DIR%\t01-baseline\index.html
echo   %REPORT_DIR%\t10-cache-breakdown\index.html
echo   %REPORT_DIR%\t11-cache-avalanche\index.html
echo   %REPORT_DIR%\s61-feign-fallback\index.html
echo   %REPORT_DIR%\s62-gateway-flow\index.html
echo JTL 结果：
echo   %RESULT_DIR%
echo 解析汇总：python "%~dp0parse-results.py"
exit /b 0

:run
set "NAME=%~1"
set "RESULT=%RESULT_DIR%\%NAME%.jtl"
set "REPORT=%REPORT_DIR%\%NAME%"
echo.
echo ===== 运行 %NAME% =====
if exist "%REPORT%" (
  echo [SKIP] 报告目录已存在，JMeter -e -o 要求目录不存在：%REPORT%
  echo        请先删除后重跑：rmdir /s /q "%REPORT%"
  exit /b 1
)
if exist "%RESULT%" del /q "%RESULT%"
call "%JMETER_BAT%" -n -t "%JMX_DIR%\%NAME%.jmx" -l "%RESULT%" -e -o "%REPORT%" %EXTRA%
if errorlevel 1 (
  echo [ERROR] %NAME% 运行失败，请查看上面的 JMeter 输出。
  exit /b 1
)
echo [OK] %NAME% -^> %REPORT%\index.html
exit /b 0
