@echo off
echo ================================
echo P2P File Sharing Application
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

REM Chạy ứng dụng GUI mode
echo Khởi động GUI mode...
echo.
java -cp bin p2p.main.Main

pause
