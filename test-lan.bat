@echo off
echo ========================================
echo LAN Discovery Test
echo ========================================
echo.

if "%1"=="" (
    set /p PORT="Enter port (e.g., 5000): "
) else (
    set PORT=%1
)

echo Compiling...
javac -d bin -cp bin src/p2p/model/*.java src/p2p/network/DiscoveryService.java test/TestLANDiscovery.java 2>nul

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Starting LAN discovery on port %PORT%
echo ========================================
echo.
echo INSTRUCTIONS:
echo 1. Run this on multiple computers in same LAN
echo 2. Make sure Firewall allows UDP 9000-9099
echo 3. Each should discover others in 5-10 seconds
echo.

java -cp bin test.TestLANDiscovery %PORT%
