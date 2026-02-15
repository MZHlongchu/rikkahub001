@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ==========================================
echo RikkaHub 容器环境单元测试
echo ==========================================
echo.

rem 检查 gradlew 是否存在
if not exist "gradlew" (
    echo [错误] 请在项目根目录运行此脚本
    exit /b 1
)

echo 步骤 1: 清理构建缓存...
.\gradlew clean --quiet 2>nul
if errorlevel 1 (
    echo [警告] 清理失败，继续执行...
)

echo.
echo 步骤 2: 运行单元测试 (JVM)...
echo ==========================================

rem 运行单元测试
.\gradlew :app:testDebugUnitTest ^
    --tests "me.rerere.rikkahub.data.container.*" ^
    --tests "me.rerere.rikkahub.data.ai.tools.LocalToolsContainerTest" ^
    --info > test-output.log 2>&1

set TEST_RESULT=%ERRORLEVEL%

echo.
echo ==========================================

if %TEST_RESULT% == 0 (
    echo [成功] 单元测试全部通过！
    echo.
    echo 测试统计:
    findstr /C:"tests completed" /C:"PASSED" /C:"FAILED" test-output.log 2>nul || echo 请查看 test-output.log 了解详情
) else (
    echo [失败] 单元测试失败
    echo.
    echo 失败详情:
    findstr /C:"FAILED" /C:"AssertionError" /C:"Exception" test-output.log | head -20 2>nul || echo 请查看 test-output.log 了解详情
)

echo.
echo ==========================================
echo 报告文件:
echo   - 控制台输出: test-output.log
echo   - HTML 报告: app\build\reports\tests\testDebugUnitTest\index.html
echo ==========================================

exit /b %TEST_RESULT%
