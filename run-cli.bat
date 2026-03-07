@echo off
echo ================================
echo P2P File Sharing - CLI Mode
echo ================================
echo.

REM Biên dịch nếu chưa có thư mục bin
if not exist "bin" (
    echo Biên dịch project...
    mkdir bin
    javac -d bin -sourcepath src src\p2p\main\Main.java
    if errorlevel 1 (
        echo Biên dịch thất bại!
        pause
        exit /b 1
    )
    echo Biên dịch thành công!
    echo.
)

REM Tạo thư mục shared nếu chưa có
if not exist "shared" (
    mkdir shared
    echo Đã tạo thư mục chia sẻ: shared\
)

REM Chạy ứng dụng CLI mode
echo Khởi động CLI mode...
echo.
java -cp bin p2p.main.Main --cli

pause
