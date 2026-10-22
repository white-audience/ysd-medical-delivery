@echo off
setlocal
rem ============================================================
rem 药速达 JMeter 一键跑全部 10 个计划：批次 1 + 批次 2
rem
rem 用法：run-all.bat -Jtoken=eyJ... -JadminToken=eyJ... -Jcid=11 -JdrugId=1 -JorderNumber=...
rem 参数会原样透传给每个 .jmx（-J 形式）
rem
rem 提醒：批次 2 的 5 个计划需要你手动注入故障（停 Redis / 停 push-service / 停 RabbitMQ /
rem       起第二 catalog 实例 / 清空购物车），详见 jmeter\README.md。缺故障注入时结果无意义。
rem ============================================================

cd /d "%~dp0"

call "%~dp0run-batch1.bat" %*
if errorlevel 1 (
  echo [WARN] 批次 1 有失败项，继续执行批次 2。
)

call "%~dp0run-batch2.bat" %*
if errorlevel 1 (
  echo [WARN] 批次 2 有失败项。
)

echo.
echo ===== 全部批次结束 =====
echo 报告目录：%~dp0reports
echo 汇总解析：python "%~dp0parse-results.py"
exit /b 0
